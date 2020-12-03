/*
 * SonarQube Java
 * Copyright (C) 2012-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks.regex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.java.checks.helpers.SimplifiedRegexCharacterClass;
import org.sonar.java.checks.helpers.SubAutomaton;
import org.sonar.java.regex.RegexParseResult;
import org.sonar.java.regex.ast.AtomicGroupTree;
import org.sonar.java.regex.ast.AutomatonState;
import org.sonar.java.regex.ast.CharacterClassElementTree;
import org.sonar.java.regex.ast.DisjunctionTree;
import org.sonar.java.regex.ast.DotTree;
import org.sonar.java.regex.ast.GroupTree;
import org.sonar.java.regex.ast.RegexBaseVisitor;
import org.sonar.java.regex.ast.RegexTree;
import org.sonar.java.regex.ast.RepetitionTree;
import org.sonar.java.regex.ast.SequenceTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;

import static org.sonar.java.checks.helpers.RegexTreeHelper.canReachWithoutConsumingInput;
import static org.sonar.java.checks.helpers.RegexTreeHelper.intersects;
import static org.sonar.java.checks.helpers.RegexTreeHelper.isAnchoredAtEnd;

@Rule(key = "S5852")
public class RedosCheck extends AbstractRegexCheckTrackingMatchType {

  private static final String MESSAGE = "Make sure the regex used here, which is vulnerable to %s backtracking," +
    " cannot lead to denial of service%s.";
  private static final String JAVA8_MESSAGE = " or upgrade to Java 9 or later, which .";
  private static final String EXP = "exponential";
  private static final String QUAD = "quadratic";

  enum BacktrackingType {
    ALWAYS_EXPONENTIAL,
    QUADRATIC_IN_9,
    FIXED_IN_9,
    NO_ISSUE
  }

  private boolean isJava9OrHigher() {
    return context.getJavaVersion().isNotSet() || context.getJavaVersion().asInt() >= 9;
  }
  
  private Optional<String> message(BacktrackingType backTrackingType) {
    switch (backTrackingType) {
      case ALWAYS_EXPONENTIAL:
        return Optional.of(String.format(MESSAGE, EXP, ""));
      case QUADRATIC_IN_9:
        return Optional.of(String.format(MESSAGE, isJava9OrHigher() ? QUAD : EXP, ""));
      case FIXED_IN_9:
        return isJava9OrHigher() ? Optional.empty() : Optional.of(String.format(MESSAGE, EXP, JAVA8_MESSAGE));
      case NO_ISSUE:
        return Optional.empty();
    }
    throw new IllegalStateException("This line is not actually reachable");
  }

  @Override
  public void checkRegex(RegexParseResult regexForLiterals, ExpressionTree methodInvocationOrAnnotation, MatchType matchType) {
    RedosFinder visitor = new RedosFinder(matchType == MatchType.FULL || matchType == MatchType.BOTH);
    visitor.visit(regexForLiterals);
    message(visitor.foundBacktrackingType).ifPresent(message ->
      reportIssue(methodOrAnnotationName(methodInvocationOrAnnotation), message, null, Collections.emptyList())
    );
  }

  private static class RedosFinder extends RegexBaseVisitor {

    private BacktrackingType foundBacktrackingType = BacktrackingType.NO_ISSUE;

    private RegexParseResult regex;
    private final boolean isUsedForFullMatch;

    public RedosFinder(boolean isUsedForFullMatch) {
      this.isUsedForFullMatch = isUsedForFullMatch;
    }

    private void addBacktracking(BacktrackingType newBacktrackingType) {
      if (newBacktrackingType.ordinal() < foundBacktrackingType.ordinal()) {
        foundBacktrackingType = newBacktrackingType;
      }
    }

    @Override
    protected void before(RegexParseResult regexParseResult) {
      regex = regexParseResult;
    }

    @Override
    public void visitRepetition(RepetitionTree tree) {
      if (foundBacktrackingType == BacktrackingType.ALWAYS_EXPONENTIAL) {
        return;
      }
      if (!tree.isPossessive() && tree.getQuantifier().isOpenEnded() && canFail(tree)) {
        new BacktrackingFinder(this, tree.isReluctant()).visit(tree.getElement());
      } else {
        super.visitRepetition(tree);
      }
    }

    private boolean canFail(@Nullable AutomatonState state) {
      if (state == null || isEmptySequence(state)) {
        return canFail(state.continuation());
      }
      if (state instanceof GroupTree) {
        return canFail(((GroupTree) state).getElement());
      }
      if (canMatchAnything(state)) {
        return false;
      }
      return isUsedForFullMatch || isAnchoredAtEnd(state) || !canReachWithoutConsumingInput(state, regex.getFinalState());
    }

    private static boolean isEmptySequence(AutomatonState state) {
      return state instanceof SequenceTree && ((SequenceTree) state).getItems().isEmpty();
    }

    private static boolean canMatchAnything(AutomatonState state) {
      if (!(state instanceof RepetitionTree)) {
        return false;
      }
      RepetitionTree repetition = (RepetitionTree) state;
      return repetition.getQuantifier().getMinimumRepetitions() == 0 && repetition.getQuantifier().isOpenEnded()
        && canMatchAnyCharacter(repetition.getElement());
    }

    private static boolean canMatchAnyCharacter(RegexTree tree) {
      SimplifiedRegexCharacterClass characterClass = new SimplifiedRegexCharacterClass();
      for (RegexTree singleCharacter : collectSingleCharacters(tree, new ArrayList<>())) {
        if (singleCharacter.is(RegexTree.Kind.DOT)) {
          characterClass.add((DotTree) singleCharacter);
        } else {
          characterClass.add((CharacterClassElementTree) singleCharacter);
        }
      }
      return characterClass.matchesAnyCharacter();
    }

    private static List<RegexTree> collectSingleCharacters(@Nullable RegexTree tree, List<RegexTree> accumulator) {
      if (tree == null) {
        return accumulator;
      }
      if (tree instanceof CharacterClassElementTree || tree.is(RegexTree.Kind.DOT)) {
        accumulator.add(tree);
      } else if (tree.is(RegexTree.Kind.DISJUNCTION)) {
        for (RegexTree alternative : ((DisjunctionTree) tree).getAlternatives()) {
          collectSingleCharacters(alternative, accumulator);
        }
      } else if (tree instanceof GroupTree) {
        collectSingleCharacters(((GroupTree) tree).getElement(), accumulator);
      } else if (tree.is(RegexTree.Kind.REPETITION)) {
        RepetitionTree repetition = (RepetitionTree) tree;
        if (repetition.getQuantifier().getMinimumRepetitions() <= 1) {
          collectSingleCharacters(repetition.getElement(), accumulator);
        }
      }
      return accumulator;
    }

  }

  private static class BacktrackingFinder extends RegexBaseVisitor {

    private final RedosFinder redosFinder;
    private final boolean isReluctant;

    public BacktrackingFinder(RedosFinder redosFinder, boolean isReluctant) {
      this.redosFinder = redosFinder;
      this.isReluctant = isReluctant;
    }

    @Override
    public void visitAtomicGroup(AtomicGroupTree tree) {
      redosFinder.visitAtomicGroup(tree);
    }

    @Override
    public void visitRepetition(RepetitionTree tree) {
      if (tree.isPossessive()) {
        redosFinder.visitRepetition(tree);
      } else if (tree.getQuantifier().isOpenEnded()) {
        redosFinder.addBacktracking(isReluctant ? BacktrackingType.ALWAYS_EXPONENTIAL : BacktrackingType.QUADRATIC_IN_9);
      } else {
        super.visitRepetition(tree);
      }
    }

    @Override
    public void visitDisjunction(DisjunctionTree tree) {
      if (containsIntersections(tree.getAlternatives())) {
        redosFinder.addBacktracking(isReluctant ? BacktrackingType.ALWAYS_EXPONENTIAL : BacktrackingType.FIXED_IN_9);
      } else {
        super.visitDisjunction(tree);
      }
    }

    boolean containsIntersections(List<RegexTree> trees) {
      for (RegexTree tree1 : trees) {
        for (RegexTree tree2 : trees) {
          SubAutomaton auto1 = new SubAutomaton(tree1, tree1.continuation(), false);
          SubAutomaton auto2 = new SubAutomaton(tree2, tree2.continuation(), false);
          if (tree1 != tree2 && intersects(auto1, auto2, false)) {
            return true;
          }
        }
      }
      return false;
    }
  }

}
