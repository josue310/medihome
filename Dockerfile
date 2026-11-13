# =====================================================================
#  Medi Home — image de production (Render, Koyeb, tout hébergeur Docker)
#  Étape 1 : compilation des sources Java.  Étape 2 : Tomcat 7 + Java 8.
#  (Java 8 plutôt que 7 : connexions TLS 1.2 vers la base MySQL hébergée.)
# =====================================================================
FROM tomcat:7.0.109-jdk8-openjdk-slim AS build
WORKDIR /build
COPY src ./src
COPY WebContent ./WebContent
RUN mkdir -p WebContent/WEB-INF/classes \
 && javac -encoding UTF-8 -source 1.7 -target 1.7 -Xlint:-options -nowarn \
      -cp "/usr/local/tomcat/lib/*:WebContent/WEB-INF/lib/*" \
      -d WebContent/WEB-INF/classes $(find src -name '*.java') \
 && cp src/*.properties WebContent/WEB-INF/classes/ \
 && rm -f WebContent/WEB-INF/pom.xml

FROM tomcat:7.0.109-jdk8-openjdk-slim
# Sécurité : suppression des applications d'exemple / d'administration de Tomcat,
# port d'arrêt désactivé ; derrière le proxy HTTPS de l'hébergeur, Tomcat prend en
# compte X-Forwarded-Proto (request.isSecure(), cookies Secure, redirections en https).
RUN rm -rf /usr/local/tomcat/webapps/* \
 && sed -i 's/<Server port="8005"/<Server port="-1"/' /usr/local/tomcat/conf/server.xml \
 && sed -i 's#</Host>#  <Valve className="org.apache.catalina.valves.RemoteIpValve" remoteIpHeader="x-forwarded-for" protocolHeader="x-forwarded-proto" internalProxies=".*" />\n      </Host>#' /usr/local/tomcat/conf/server.xml \
 && mkdir -p /tmp/uploads
# Application servie à la racine du domaine (https://<app>.onrender.com/)
COPY --from=build /build/WebContent /usr/local/tomcat/webapps/ROOT
COPY deploy/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENV UPLOAD_DIR=/tmp/uploads \
    STUN_URLS=stun:stun.l.google.com:19302
EXPOSE 8080
CMD ["/usr/local/bin/docker-entrypoint.sh"]
