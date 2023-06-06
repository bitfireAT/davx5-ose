
**Thank you for your interest in contributing to DAVx⁵!**


# Licensing

All work in this repository is [licensed under the GPLv3](LICENSE).

We (bitfire.at, initial and main contributors) are also asking you to give us
permission to use your contribution for related non-open source projects
like [Managed DAVx⁵](https://www.davx5.com/organizations/managed-davx5).

If you send us a pull request, our CLA bot will ask you to sign the
Contributor's License Agreement so that we can use your contribution.


# Copyright

Make sure that every file that contains significant work (at least every code file)
starts with the copyright header:

```
/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
```

You can set this in Android Studio:

1. Settings / Editor / Copyright / Copyright Profiles
2. Paste the text above (without the stars).
3. Set Formatting so that the preview exactly looks like above; one blank line after the block.
4. Set this copyright profile as the default profile for the project.
5. Apply copyright: right-click in file tree / Update copyright.


# Style guide

Please adhere to the [Kotlin style guide](https://developer.android.com/kotlin/style-guide) and
the following hints to make the source code uniform.

**Have a look at similar files and copy their style if you're not certain.**

Sample file (pay attention to blank lines and other formatting):

```
<Copyright header, see above>

class MyClass(int arg1) : SuperClass() {

    companion object {

        const val CONSTANT_STRING = "Constant String";

        fun staticMethod() {	// Use static methods when you don't need the object context.
            // …
        }

    }

    var someProperty: String = "12345"
    var someRelatedProperty: Int = 12345

    init {
        // constructor
    }


    /**
     * Use KDoc to document important methods. Don't use it dogmatically, but writing proper documentation
     * (not just the method name with spaces) helps you to re-think what the method shall really do.
     */
    fun aFun1() {		// Group methods by some logic (for instance, the order in which they will be called)
    }				// and alphabetically within a group.

    fun anotherFun() {
        // …
    }


    fun somethingCompletelyDifferent() {	// two blank lines to separate groups
    }

    fun helperForSomethingCompletelyDifferent() {
        someCall(arg1, arg2, arg3, arg4)	// function calls: stick to one line unless it becomes confusing
    }


    class Model(				// two blank lines before inner classes
        someArgument: SomeLongClass,		// arguments in multiple lines when they're too long for one line
        anotherArgument: AnotherLongType,
        thirdArgument: AnotherLongTypeName
    ) : ViewModel() {

        fun abc() {
        }

    }

}
```

In general, use one blank line to separate things within one group of things, and two blank lines
to separate groups. In rare cases, when methods are tightly coupled and are only helpers for another
method, they may follow the calling method without separating blank lines.

## Tests

Test classes should be in the appropriate directory (see existing tests) and in the same package as the
tested class. Tests are usually be named like `methodToBeTested_Condition()`, see
[Test apps on Android](https://developer.android.com/training/testing/).


# Authors

If you make significant contributions, feel free to add yourself to the [AUTHORS file](AUTHORS).

