# Android Testing Skill (Java + XML)

Create and execute minimal tests for Android apps. Supports JUnit unit tests and Espresso UI tests. Assume a minimal standard test setup is already present — only add dependencies if strictly required.

---

## Prerequisites (minimal)

Assume a standard Android testing setup (JUnit 4 + Espresso) is already present. Only add missing dependencies if required.

---

## Unit Tests (JUnit 4)

### Checklist

1. Location: `app/src/test/java/<your_package>/`
2. Test only pure Java logic (no Android framework dependencies)
3. Use assertions: `assertEquals`, `assertTrue`, etc.
4. Run with:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### Example

```java
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleUnitTest {
    @Test
    public void simpleTest() {
        assertTrue(true);
    }
}
```

---

## Instrumented / UI Tests (Espresso)

### Checklist

1. Location: `app/src/androidTest/java/<your_package>/`
2. Annotate with `@RunWith(AndroidJUnit4.class)`
3. Use `ActivityScenarioRule` or `ActivityScenario.launch()`
4. Find UI elements via `R.id.*`
5. Perform interaction → verify UI or navigation
6. Run with:
   ```bash
   ./gradlew connectedDebugAndroidTest
   ```

### Example: UI element check

```java
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class ExampleUiTest {

    @Rule
    public ActivityScenarioRule</* ActivityUnderTest */> rule =
        new ActivityScenarioRule<>(/* ActivityUnderTest.class */);

    @Test
    public void testElementVisible() {
        onView(withId(R.id.some_button))
            .check(matches(isDisplayed()));
    }
}
```

### Example: Navigation test (intent-based)

```java
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.espresso.intent.Intents;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.Intents.intended;

@RunWith(AndroidJUnit4.class)
public class NavigationTest {

    @Test
    public void testNavigation() {
        Intents.init();
        try {
            // Launch activity under test
            ActivityScenario.launch(/* ActivityUnderTest.class */);

            onView(withId(R.id.button)).perform(click());

            intended(hasComponent(/* TargetActivity.class.getName() */));
        } finally {
            Intents.release();
        }
    }
}
```

### Common UI actions

- Verify text:
  ```java
  onView(withId(R.id.text)).check(matches(withText(R.string.label)));
  ```

- Click:
  ```java
  onView(withId(R.id.button)).perform(click());
  ```

- Type input:
  ```java
  onView(withId(R.id.input))
      .perform(typeText("hello"), closeSoftKeyboard());
  ```

- Visibility:
  ```java
  onView(withId(R.id.view)).check(matches(isDisplayed()));
  ```

---

## Scope & Constraints

### Include
- Basic unit tests for pure logic (no Android framework classes)
- Minimal Espresso UI tests
- One test per behavior
- Simple navigation verification via intents

### Do NOT include
- Full coverage strategies
- Complex asynchronous handling
- Device/rotation testing
- Compose testing
- Advanced mocking frameworks
- Screenshot testing
- E2E / device-only tests (UIAutomator, Appium)
- CI/CD or pipeline integration

---

## Project Defaults (SwissTrainSpotting)

These are examples only and must not define the core skill.

- Project name: SwissTrainSpotting
- Package: `com.tb.swisstrainspotting`
- Primary activity: MainActivity
- Secondary activity: ImageClassificationActivity
- Navigation: button-triggered intents
- Flow: image selection → classification → result display

Paths:
- Unit tests: `app/src/test/java/com/tb/swisstrainspotting/`
- UI tests: `app/src/androidTest/java/com/tb/swisstrainspotting/`
- Assets: `app/src/main/assets/`

The skill remains valid for any activity or flow as the project evolves.

---

## References

This skill is intentionally minimal and execution-focused. Use the guidance above first.

For deeper technical details, consult the canonical reference files located at:

`../external/android-skills/testing/testing-setup/references/`

Use these references only when:
- Espresso or JUnit API usage needs clarification
- AndroidX testing behavior or configuration is unclear
- additional implementation detail is required beyond the minimal patterns above

Do NOT:
- expand the scope of testing (keep it minimal as defined in this skill)
- introduce additional frameworks or advanced patterns not already allowed

The references provide supporting detail, not additional requirements. The rules and constraints in this SKILL.md always take precedence.