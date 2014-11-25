# Android application Bash build script
# v.2013-03-22 16:19 yvolk http://yurivolkov.com/
#
# Successfully tested this script without ANY modifications:
# 1. In these build environments:
# 1.1. "Terminal IDE" on an Android device, see 
#     https://play.google.com/store/apps/details?id=com.spartacusrex.spartacuside&hl=en
# 1.2. Cygwin ( http://www.cygwin.com/ ) under Windows 7  
#      Note: Bash scripts in cygwin for 'aapt' 'apkbuilder' 'dx' and 'signer' should be created though.
# 2. For these two applications:
# 2.1. "demo_android" - built-in "Terminal IDE" demo application
# 2.2. AndStatus application ( http://andstatus.org/andstatus/ )
#
# For more details see:
# https://github.com/andstatus/andstatus/wiki/Building-the-Android-application-using-Bash-script  
#
# Notes :
#   1. In order to sign your app with custom key on Android device use ZipSigner, 
#   see http://stackoverflow.com/questions/14018002/how-to-sign-apk-file-on-an-android-phone/15565685#15565685

# Change this to the desired name of the target signed .apk file
APKNAME="signed.apk"

# Edit next two lines if you need up to two specific alternative paths to search for anroid.jar
ANDROID_JAR_PATH="/storage/extSdCard/w/Android/sdk/platforms/android-15/android.jar"
ANDROID_JAR_PATH_ALT="c:/android/android-sdk-windows/platforms/android-15/android.jar"

# Decomment this (or better 'export' to environment) for Windows:
#PATH_SEPARATOR=";"

if [ ! $PATH_SEPARATOR ]
then
	# This is NOT for Windows
	PATH_SEPARATOR=":"
    echo "Setting path separator to '$PATH_SEPARATOR'"
fi 

#cd into the home dir of the project - this way it works when run from inside vim or any other folder
DIR="$( cd "$( dirname "$0" )" && pwd )"
echo "----------------------------------"
echo "[builder] Build of the 'bin/$APKNAME.apk' started in '$DIR'"
cd $DIR
if [ ! -f "builder.sh" ]; then
	echo "We're in the wrong directory ?!"
	exit 1
fi
echo "----------------------------------"
echo "[builder] Checking files existence"
# On using boolean variables in bash see: http://stackoverflow.com/questions/2953646/how-to-declare-and-use-boolean-variables-in-shell-script
found=false
if ! $found; then
	if [ $ANDROID_JAR_PATH ]; then
		if [ -f $ANDROID_JAR_PATH ]; then
			found=true
		fi
	fi
fi
if ! $found; then
	if [ $ANDROID_JAR_PATH_ALT ]; then
		ANDROID_JAR_PATH=$ANDROID_JAR_PATH_ALT
		if [ -f $ANDROID_JAR_PATH ]; then
			found=true
		fi
	fi
fi
if ! $found; then
	#Default relative location for the Terminal IDE samples
	ANDROID_JAR_PATH="../../../system/classes/android.jar"
	if [ -f $ANDROID_JAR_PATH ]; then
		found=true
	fi
fi
if ! $found; then
	#Default location for Terminal IDE which does'n work on my device
	ANDROID_JAR_PATH="~/system/classes/android.jar"
	if [ -f $ANDROID_JAR_PATH ]; then
		found=true
	fi
fi
if $found; then
	echo "android.jar found at $ANDROID_JAR_PATH"
else
    echo "Error: couldn't find android.jar"
    exit 1
fi 

#Clean up but don't delete these root directories (same behaviour as of ADT )
rm bin/* -r
rm gen/* -r

#create the needed directories
mkdir -m 770 -p gen
mkdir -m 770 -p bin/classes

#Now use aapt
echo "----------------------------------"
echo "[builder] Create the R.java files"

aapt p -f -v -M AndroidManifest.xml -F bin/resources.ap_ -I $ANDROID_JAR_PATH -S res -J gen
RETCODE=$?
if [ $RETCODE -gt 0 ]
then
	echo "Exit code $RETCODE"
	exit 1
fi  

echo "----------------------------------"
echo "[builder] Generate .java files from .aidl files"
AIDLTOOLFOUND=0
INCLUDEGENAIDIL=0			
NAIDL=$(find src -name *.aidl | wc -l)
# See http://www.thegeekstuff.com/2010/06/bash-conditional-expression/
if [ $NAIDL -gt 0 ] 
then
	echo "$NAIDL .aidl files found, checking for aidl tool existence."
	AIDLPATH=$(which aidl)
	if [ ${#AIDLPATH} -gt 5 ]
	then
		if [ -f "$AIDLPATH" ]
		then
			AIDLTOOLFOUND=1
			echo "aidl tool found at '$AIDLPATH'. Path length= ${#AIDLPATH}"
			find src -iname *.aidl >bin/aidlfiles.txt
			# See http://en.kioskea.net/faq/1757-how-to-read-a-file-line-by-line
			while read line           
			do           
				echo Processing: $line
				aidl -Isrc -ogen $line
			done <bin/aidlfiles.txt
		fi 
	fi 
	if [ $AIDLTOOLFOUND -eq 0 ]
	then
		echo "Aidl tool not found. 'which' command returned '$AIDLPATH'"
		if [ -d genaidl ]
		then
	    	echo "Warning: Using pre-generated files from 'genaidl' directory"
			INCLUDEGENAIDIL=1			
		else
	    	echo "Error: cannot find not aidl tool nor pre-generated .java files"
	    	exit 1
	    fi
	fi 
else
	echo "No .aidl files found, skipping." 
fi

# About multiple src files: http://stackoverflow.com/questions/8478386/javac-how-to-compile-whole-source-dir-with-wildcard
echo "----------------------------------"
echo "[builder] Prepare source files list"
find gen -iname *.java >bin/srcfiles.txt
find src -iname *.java >>bin/srcfiles.txt
if [ $INCLUDEGENAIDIL -eq 1 ]
	then
	find genaidl -iname *.java >>bin/srcfiles.txt
fi

# We will prepend bootstrap class path with it in order to override
#   preset default android.jar
BCP=$ANDROID_JAR_PATH
export 	BOOTCLASSPATH=$BCP$PATH_SEPARATOR$BOOTCLASSPATH

# List all libs from the 'libs' directory
for i in libs/*.jar
do
  CP=$(echo -n "$CP$i$PATH_SEPARATOR")
done
#CP=$CP$ANDROID_JAR_PATH
echo "Bootclasspath: $BCP"
echo "Classpath: $CP"

# Now compile. Note the use of a separate lib (in non-dex format!)
# see http://stackoverflow.com/questions/2096283/including-jars-in-classpath-on-commandline-javac-or-apt
echo "----------------------------------"
echo "[builder] Compile the java code"
javac -verbose "-Xbootclasspath/p:$BCP" -cp $CP -d bin/classes -encoding UTF-8 @bin/srcfiles.txt
# See http://stackoverflow.com/questions/90418/exit-shell-script-based-on-process-exit-code
RETCODE=$?
if [ $RETCODE -gt 0 ]
then
	echo "Exit code $RETCODE"
	exit 1
fi  

#Now convert to dex format (need --no-strict) (Notice demolib.jar at the end - non-dex format)
echo "----------------------------------"
echo "[builder] Now convert to dex format"
# 'dx' tool in TerminalIde fails to accept the -J argument e.g.: -J-Xmx100m
# So in order to get rid of the OutOfMemory error I edited the "dx" script directly by
# putting the argument immediately after "dalvikvm":
# dalvikvm -Xmx100m ...
dx --dex --verbose --no-strict --output=bin/classes.dex bin/classes libs
RETCODE=$?
if [ $RETCODE -gt 0 ]
then
	echo "Exit code $RETCODE"
	exit 1
fi  

#And finally - create the .apk
echo "----------------------------------"
echo "[builder] Create an unsigned apk"
apkbuilder bin/unsigned.apk -v -u -z bin/resources.ap_ -f bin/classes.dex 
RETCODE=$?
if [ $RETCODE -gt 0 ]
then
	echo "Exit code $RETCODE"
	exit 1
fi  

#And now sign it
echo "----------------------------------"
echo "[builder] Sign the apk"
signer bin/unsigned.apk bin/$APKNAME.apk
RETCODE=$?
if [ $RETCODE -gt 0 ]
then
	echo "Exit code $RETCODE"
	exit 1
fi  
echo "----------------------------------"
echo "[builder] Build of the bin/$APKNAME.apk completed successfully"
