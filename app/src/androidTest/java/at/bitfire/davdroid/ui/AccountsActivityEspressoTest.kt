package at.bitfire.davdroid.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isClosed
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.filters.LargeTest
import at.bitfire.davdroid.R
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeoutException

@LargeTest
class AccountsActivityEspressoTest {

    @get:Rule
    val activityScenarioRule = activityScenarioRule<AccountsActivity>()

    private val username = "test"
    private val password = "test"
    private val baseUrl = "https://davtest.dev001.net/radicale/htpasswd/"

    // FIXME should work
    /*
    @Test
    fun accountsActivityTest() {
        skipIntroActivity()

        onView(withId(R.id.fab)).perform(click())

        // open first the option for Login with Base URL and then enter the test-data and confirm
        onView(withText(R.string.login_type_url)).perform(click())
        onView(withId(R.id.loginUrlBaseUrlEdittext)).perform(typeText(baseUrl), ViewActions.closeSoftKeyboard())
        onView(withId(R.id.loginUrlUsernameEdittext)).perform(typeText(username), ViewActions.closeSoftKeyboard())
        onView(withId(R.id.loginUrlPasswordEdittext)).perform(typeText(password), ViewActions.closeSoftKeyboard())
        onView(withId(R.id.login)).perform(click())

        // The detect configuration screen (detect_configuration.xml) is not asserted here, it's just skipped.
        // login_account_details.xml is the next expected fragment, check if the expected headline appears and then click on the "Create Account" button
        onView(isRoot()).perform(waitForView(R.id.accountName, 30000))

        onView(withText(R.string.login_account_name_info)).check(matches(isDisplayed()))
        onView(withId(R.id.create_account)).perform(click())

        Thread.sleep(1000)

        // check if the "test" calendar appeared
        onView(isRoot()).perform(waitForView(R.id.title, 5000))
        //onView(withId(R.id.title)).perform(waitForText("Test Addressbook", 30000))
        onView(withText("Test Addressbook")).check(matches(withText("Test Addressbook")))

        // Go back to the overview with all Accounts
        Espresso.pressBack()
        //Thread.sleep(2000)

        // check if the account exists and click on it
        onView(isRoot()).perform(waitForView((R.id.account_name), 5000))
        onView(withText("test")).check(matches(withText("test")))
        onView(withText("test")).perform(click())

        // open the overflowMenu to delete the account
        val overflowMenuButton = onView(
                Matchers.allOf(withContentDescription("More options"),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.toolbar),
                                        2),
                                1),
                        isDisplayed()))
        overflowMenuButton.perform(click())

        // click on the delete button
        onView(withText(R.string.account_delete)).perform(click())
        onView(withText(R.string.account_delete_confirmation_title)).check(matches(isDisplayed()))
        // confirm deletion by clicking on YES
        onView(withId(android.R.id.button1)).perform(click())

        // doublecheck to make sure that the account doesn't exist anymore. The welcome text is displayed
        onView(withText(R.string.account_list_empty)).check(matches(withText(R.string.account_list_empty)))
    }
    */

    @Test
    fun menuDrawerTest() {
        skipIntroActivity()

        // TESTING ABOUT DIALOG
        // Open Drawer to click on navigation.
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT))) // Left Drawer should be closed.
                .perform(DrawerActions.open()) // Open Drawer
        // check if about can be opened
        onView(withText(R.string.navigation_drawer_about)).perform(click())
        onView(withText(R.string.about_copyright)).check(matches(isDisplayed()))
        Espresso.pressBack()

        // TESTING SETTINGS DIALOG
        // Open Drawer to click on navigation.
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT))) // Left Drawer should be closed.
                .perform(DrawerActions.open()) // Open Drawer
        // check if about can be opened
        onView(withText(R.string.navigation_drawer_settings)).perform(click())
        onView(withText(R.string.app_settings_show_debug_info)).check(matches(isDisplayed()))
        Espresso.pressBack()

        // TESTING WEBSITE MENU ENTRY
        // Open Drawer to click on navigation.
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT))) // Left Drawer should be closed.
                .perform(DrawerActions.open()) // Open Drawer
        // check if Website can be opened
        //onView(withText(R.string.navigation_drawer_website)).perform(click())
    }


    private fun childAtPosition(
            parentMatcher: Matcher<View>, position: Int): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }

    private fun skipIntroActivity() {
        try {
            onView(withId(R.id.takeControl)).check(matches(withText(R.string.intro_slogan2)))   // intro_welcome is the first fragment, check first if the String Resource "intro_slogan2" is shown.
            // click through up to 5 intro fragments
            for (i in 1..5)
                try {
                    onView(withId(R.id.next)).perform(click())
                } catch (ignored: Exception) { }
            onView(withId(R.id.done)).perform(click())
        } catch (ignored: NoMatchingViewException) {
            // the IntroActivity or some fragments of it may not show up every time
        }
        onView(withText(R.string.account_list_empty)).check(matches(isDisplayed()))
    }

    /**
     * This ViewAction tells espresso to wait till a certain view is found in the view hierarchy.
     * Source: https://www.repeato.app/espresso-wait-for-view/
     * @param viewId The id of the view to wait for.
     * @param timeout The maximum time which espresso will wait for the view to show up (in milliseconds)
     */
    private fun waitForView(viewId: Int, timeout: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }

            override fun getDescription(): String {
                return "wait for a specific view with id $viewId; during $timeout millis."
            }

            override fun perform(uiController: UiController, rootView: View) {
                uiController.loopMainThreadUntilIdle()
                val startTime = System.currentTimeMillis()
                val endTime = startTime + timeout
                val viewMatcher = withId(viewId)

                do {
                    // Iterate through all views on the screen and see if the view we are looking for is there already
                    for (child in TreeIterables.breadthFirstViewTraversal(rootView)) {
                        // found view with required ID
                        if (viewMatcher.matches(child)) {
                            return
                        }
                    }
                    // Loops the main thread for a specified period of time.
                    // Control may not return immediately, instead it'll return after the provided delay has passed and the queue is in an idle state again.
                    uiController.loopMainThreadForAtLeast(100)
                } while (System.currentTimeMillis() < endTime) // in case of a timeout we throw an exception -&gt; test fails
                throw PerformException.Builder()
                        .withCause(TimeoutException())
                        .withActionDescription(this.description)
                        .withViewDescription(HumanReadables.describe(rootView))
                        .build()
            }
        }
    }

}