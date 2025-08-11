#!/bin/bash

scriptPath="$(sourcePath=`readlink -f "$0"` && echo "${sourcePath%/*}")"
basePath="$(cd $scriptPath && pwd)"

cd $basePath

rm -rf /home/thiago/.m2/repository/org/teiid

set -e

mvn clean install -P dev -s "$basePath/settings.xml" -N
mvn clean install -P dev -s "$basePath/settings.xml" -pl common-core,client,api
mvn clean install -P dev -s "$basePath/settings.xml" -pl engine,cache-caffeine,metadata,optional-geo,optional-json,saxon-xom,optional-xml

cd connectors
  mvn clean install -P dev -s "$basePath/settings.xml" -N
  mvn clean install -P dev -s "$basePath/settings.xml" -pl file
  cd file
    mvn clean install -P dev -s "$basePath/settings.xml" -pl file-api,translator-file
  cd ..
cd ..

mvn clean install -P dev -s "$basePath/settings.xml" -pl runtime

cd connectors

  mvn clean install -P dev -s "$basePath/settings.xml" -pl jdbc,webservice,odata,infinispan
  cd jdbc
    mvn clean install -P dev -s "$basePath/settings.xml" -pl translator-jdbc
  cd ..
  cd webservice
    mvn clean install -P dev -s "$basePath/settings.xml" -pl translator-ws,ws-cxf
  cd ..
  cd odata
    mvn clean install -P dev -s "$basePath/settings.xml" -pl translator-odata
  cd ..
cd ..

cd connectors
  mvn clean install -P dev -s "$basePath/settings.xml" -pl misc
  cd misc
     mvn clean install -P dev -s "$basePath/settings.xml" -pl translator-loopback
   cd ..
cd ..
mvn clean install -P dev -s "$basePath/settings.xml" -pl olingo-common,olingo

cd connectors
  mvn clean install -P dev -s "$basePath/settings.xml" -pl document-api
  cd odata
    mvn clean install -P dev -s "$basePath/settings.xml" -pl translator-odata4
  cd ..
cd ..

cd wildfly
  mvn clean install -P dev -s "$basePath/settings.xml" -N

  mvn clean install -P dev -s "$basePath/settings.xml" -pl resource-spi,connectors

  cd connectors
    mvn clean install -P dev -s "$basePath/settings.xml" -pl webservice

    cd webservice
      mvn clean install -P dev -s "$basePath/settings.xml" -pl connector-ws
    cd ..
  cd ..
cd ..