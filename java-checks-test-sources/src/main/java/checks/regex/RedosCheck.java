package checks.regex;

import javax.validation.constraints.Email;

public class RedosCheck {

  @Email(regexp = "(.*-)*@.*") // Noncompliant [[sc=4;ec=9]] {{Make sure the regex used here cannot lead to denial of service.}}
  String email;

  void noncompliant(String str) {
    str.matches("(.*,)*"); // Noncompliant [[sc=9;ec=16]] {{Make sure the regex used here cannot lead to denial of service.}}
    str.matches("(.*,)*.*"); // Noncompliant
    str.split("(.*,)*$"); // Noncompliant
    str.split("(.*,)*X"); // Noncompliant
    str.matches("(.*,)*$"); // Noncompliant
    str.matches("(.*,)*X"); // Noncompliant
    str.matches("(.?,)*X"); // Noncompliant
    str.matches("(.*?,)+"); // Noncompliant
    str.matches("(.*?,){5,}"); // Noncompliant
    str.matches("((.*,)*)*+"); // Noncompliant
    str.matches("((.*,)*)?"); // Noncompliant
    str.matches("(?>(.*,)*)"); // Noncompliant
    str.matches("((?>.*,)*)*"); // Noncompliant
    str.matches("(.*,)* (.*,)*"); // Noncompliant
  }

  void compliant(String str) {
    str.split("(.*,)*");
    str.matches("(?s)(.*,)*.*");
    str.matches("(.*,)*[\\s\\S]*");
    str.matches("(.*,)*(.|\\s)*");
    str.matches("(x?,)?");
    str.matches("(?>.*,)*");
    str.matches("([^,]*+,)*");
    str.matches("(.*?,){5}");
    str.matches("(.*?,){1,5}");
    str.matches("([^,]*,)*");
    str.matches("(;?,)*");
    str.matches("(;*,)*");
    str.matches("(.*,)*("); // Rule is not applied to syntactically invalid regular expressions
  }

}
