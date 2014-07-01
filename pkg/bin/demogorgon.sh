#!/bin/bash
PATH=/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/sbin
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROGRAM="demogorgon"
LOGFILE="$DIR/../logs/$PROGRAM.out"
PIDFILE="$DIR/../var/$PROGRAM.pid"
JAR="$DIR/../lib/$PROGRAM.jar"
JAVA_OPTS=-Xmx128m

get_pid() {
    cat "$PIDFILE"
}

is_running() {
    [ -f "$PIDFILE" ] && ps `get_pid` > /dev/null 2>&1
}

case "$1" in
    start)
        if is_running; then
            echo "Already started"
        else
            echo "Starting $PROGRAM"
            nohup java $JAVA_OPTS -jar "$JAR" </dev/null >> "$LOGFILE" 2>&1 &
            echo $! > "$PIDFILE"
            if ! is_running; then
                echo "Failed to start"
                exit 1
            fi
        fi
        ;;
    stop)
        if is_running; then
            echo -n "Stopping $PROGRAM.."
            kill `get_pid`
            for i in {1..10}
            do
                if ! is_running; then
                    break
                fi
                
                echo -n "."
                sleep 1
            done
            echo
            
            if is_running; then
                echo "Not stopped; may still be shutting down or shutdown may have failed"
                exit 1
            else
                echo "Stopped"
                if [ -f "$PIDFILE" ]; then
                    rm "$PIDFILE"
                fi
            fi
        else
            echo "Not running"
        fi
        ;;
    restart)
        $0 stop
        if is_running; then
            echo "Unable to stop, will not attempt to start"
            exit 1
        fi
        $0 start
        ;;
    status)
        if is_running; then
            echo "Running"
        else
            echo "Stopped"
            exit 1
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;  
esac

exit 0

