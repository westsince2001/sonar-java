package checks;

import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.intThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MockitoArgumentMatchersUsedOnAllParameters {
  static class Foo {
    Object bar(int a, Object b, Object c) {
      return null;
    }

    String baz(Object a, Object b) {
      return "hi";
    }

    void quux(Object a, Object b) {
    }
  }

  @Test
  public void nonCompliant() {
    Foo foo = new Foo();
    Integer i1 = null, i2 = null, val1 = null, val2 = null;
    given(foo.bar(anyInt(), i1, i2)).willReturn(null); // Noncompliant
    when(foo.baz(eq(val1), val2)).thenReturn("hi"); // Noncompliant
    doThrow(new RuntimeException()).when(foo).quux(intThat(x -> x >= 42), -1); // Noncompliant
    verify(foo).bar(i1, anyInt(), i2); // Noncompliant
  }

  @Test
  public void compliant() {
    Foo foo = new Foo();
    Integer i1 = null, i2 = null, val1 = null, val2 = null;
    given(foo.bar(anyInt(), eq(i1), eq(i2))).willReturn(null);
    when(foo.baz(val1, val2)).thenReturn("hi");
    doThrow(new RuntimeException()).when(foo).quux(intThat(x -> x >= 42), eq(-1));
    verify(foo).bar(eq(i1), anyInt(), eq(i2));
  }
}
