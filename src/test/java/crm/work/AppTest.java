package crm.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppTest {
  @Test
  void greeting() {
    assertEquals("mcloud", App.greeting());
  }
}
