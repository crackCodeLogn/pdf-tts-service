APP_NAME="pdf-tts-service"
APP_VERSION="0.0.1-SNAPSHOT"
JAVA_PARAM="-Xmx1g"

BIN_PATH=$TWM_HOME_PARENT/$APP_NAME/bin
JAR_PATH=$BIN_PATH/../target/$APP_NAME-$APP_VERSION.jar

echo "Starting '$APP_NAME' with java param: '$JAVA_PARAM', at '$JAR_PATH', TTS_KEY: '$TTS_KEY'"
java $JAVA_PARAM -Dtts.key=$TTS_KEY -jar $JAR_PATH
