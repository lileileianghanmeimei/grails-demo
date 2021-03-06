import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.util.FileSize
import grails.util.BuildSettings
import grails.util.Environment
import groovy.json.JsonBuilder
import org.springframework.boot.logging.logback.ColorConverter

conversionRule("clr", ColorConverter)

def LOG_LEVEL_PATTERN = System.getProperty("LOG_LEVEL_PATTERN")?:'%5p'
def PID = System.getProperty("PID")?:"- "
def LOG_EXCEPTION_CONVERSION_WORD = System.getProperty("LOG_EXCEPTION_CONVERSION_WORD")?:'%xEx'

def CONSOLE_LOG_PATTERN = "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} " +
        "%clr(${LOG_LEVEL_PATTERN}) %clr(${PID}){magenta} " +
        "%clr(---){faint} %clr([%15.15t]){faint}" +
        "%clr(%-40.40logger{39}){cyan} " +
        "%clr(:){faint} %m%n${LOG_EXCEPTION_CONVERSION_WORD}"

appender('CONSOLE', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = CONSOLE_LOG_PATTERN
    }
}

def env = System.getenv()
def service = env['SERVICE_NAME'] ?: 'demo-limei'
def logdir = (env.LOG_DIR ?: "/opt/log/stash") + "/${service}"
def logFile = "${logdir}/${service}.log"

def additionalFields = new JsonBuilder([
        "env": System.getProperty("grails.env"),
        "service": service,
        "su": env["service_unit_key"]?:'',
]).toPrettyString()


def var =appender("FILE", RollingFileAppender) {
    file = logFile
    encoder(net.logstash.logback.encoder.LogstashEncoder) {
        customFields = additionalFields
        includeMdc = true
    }
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = "${logdir}/${service}-%d{yyyy-MM-dd-HH}.gz"
        maxHistory = 48
        totalSizeCap = FileSize.valueOf("64GB")
    }
}



appender("ASYNC", AsyncAppender) {
    appenderRef("FILE")
    queueSize = 524288
    discardingThreshold = 32768
}

appender('CONSOLE_JSON', ConsoleAppender) {
    encoder(net.logstash.logback.encoder.LogstashEncoder) {
        customFields = additionalFields
        includeMdc = true
    }
}

if(Environment.current == Environment.DEVELOPMENT) {
    def targetDir = BuildSettings.TARGET_DIR
    if(targetDir) {

        appender("FULL_STACKTRACE", FileAppender) {

            file = "${targetDir}/stacktrace.log"
            append = true
            encoder(PatternLayoutEncoder) {
                pattern = "%level %logger - %msg%n"
            }
        }
        logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false )
    }
    root(INFO, ["CONSOLE", "ASYNC"])

} else if (env.LOG_TO_CONSOLE) {
    if (env.LOG_USE_JSON) {
        root(INFO, ["CONSOLE_JSON"])
    } else {
        root(INFO, ["CONSOLE"])
    }
} else {
    root(INFO, ["ASYNC"])
}

logger("org.springframework.boot.autoconfigure.security", INFO)
logger("org.springframework.security.web.authentication", OFF)

logger('org.apache.kafka.clients.FetchSessionHandler', WARN)
