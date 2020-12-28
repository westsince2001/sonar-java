package checks;

public class MockitoArgumentMatchersUsedOnAllParameters {
  public void nonCompliant() {
    given(foo.bar(anyInt(), i1, i2)).willReturn(null); // Noncompliant
    when(foo.baz(eq(val1), val2)).thenReturn("hi"); // Noncompliant
    doThrow(new RuntimeException()).when(foo).quux(intThat(x -> x >= 42), -1); // Noncompliant
    verify(foo).bar(i1, anyInt(), i2); // Noncompliant
  }

  public void compliant() {
    given(foo.bar(anyInt(), eq(i1), eq(i2))).willReturn(null);
    when(foo.baz(val1, val2)).thenReturn("hi");
    doThrow(new RuntimeException()).when(foo).quux(intThat(x -> x >= 42), eq(-1));
    verify(foo).bar(eq(i1), anyInt(), eq(i2));
  }
}
