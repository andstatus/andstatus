# Instructions for AndStatus project developers

## Contribute to the AndStatus Project
Did you think about coding your own features for AndStatus and wanted to know how to start?

With a few easy steps you can start developing AndStatus features:

### 1. Configure development environment on your desktop PC
* Install Java, GIT, [Android Studio bundle](http://developer.android.com/sdk/index.html)
* [Check out the source](https://github.com/andstatus/andstatus) or clone AndStatus _GIT_ repository ...
* Optionally integrate Project issues published at [Issues](https://github.com/andstatus/andstatus/issues) with Android Studio.
* Optionally [Use SonarQube for static code analysis](https://www.sonarsource.com/products/codeanalyzers/sonarjava.html).

## Testing and Debugging
1. Set the logging level of the whole application to VERBOSE using the "Minimum logging level"
   option in the "Preferences". [More on logging here](https://github.com/andstatus/andstatus/issues/225).
2. Use and enhance a suite of automated tests to help you debug your code and understand
   how the System works. The tests in this suite not only do Unit testing of components,
   but they also create AndStatus accounts locally, add messages to the database,
   open activities and press buttons on them. And of course, they check results.
   As a result, the Test suite is almost an Integration test of all local components.
   Only connection to remote servers is not tested automatically.
   Test suite is located under ["androidTest" directory](https://github.com/andstatus/andstatus/tree/master/app/src/androidTest).
4. Check and fix your code using modern tools of static code analysis.
   AndStatus has a configuration section
   for launching [SonarQube](https://sonarcloud.io/dashboard?id=andstatus)
