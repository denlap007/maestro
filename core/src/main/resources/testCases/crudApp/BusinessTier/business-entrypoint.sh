#!/bin/bash
set -e

cd /broker/testCases/crudApp/BusinessTier && echo "Changed workDir"
cp mysql-connector-java-5.1.23-bin.jar /usr/local/glassfish4/glassfish/domains/domain1/lib && echo "Copied mySqlConnectorJ to glassfish path"
asadmin start-domain -v