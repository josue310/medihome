#!/bin/sh
# Démarrage de Tomcat sur le port imposé par l'hébergeur (variable PORT, ex. Render).
set -e
PORT="${PORT:-8080}"
sed -i "s/<Connector port=\"8080\"/<Connector port=\"${PORT}\"/" /usr/local/tomcat/conf/server.xml
# Mémoire adaptée aux offres gratuites (512 Mo) ; entropie non bloquante pour SecureRandom.
export CATALINA_OPTS="${CATALINA_OPTS} -Xms128m -Xmx320m -Xss512k -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"
echo "Medi Home : démarrage de Tomcat sur le port ${PORT}"
exec /usr/local/tomcat/bin/catalina.sh run
