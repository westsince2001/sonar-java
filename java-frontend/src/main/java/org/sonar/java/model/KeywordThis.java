/*
 * SonarQube Java
 * Copyright (C) 2012-2019 SonarSource SA
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
package org.sonar.java.model;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.sonar.java.model.expression.IdentifierTreeImpl;
import org.sonar.java.resolve.Symbols;
import org.sonar.plugins.java.api.semantic.Symbol;

import javax.annotation.Nullable;

final class KeywordThis extends IdentifierTreeImpl {

  KeywordThis(InternalSyntaxToken token, @Nullable ITypeBinding typeBinding) {
    super(token);
    this.typeBinding = typeBinding;
  }

  Symbol resolveSymbol() {
    if (typeBinding == null) {
      return Symbols.unknownSymbol;
    }
    return root.sema.typeSymbol(typeBinding).thisSymbol;
  }

  @Override
  public Symbol symbol() {
    if (root.useNewSema) {
      return resolveSymbol();
    }
    return super.symbol();
  }

}