#!/bin/bash
set -e

asadmin delete-jdbc-resource ${JDBC_RESOURCE_NAME}
asadmin delete-jdbc-connection-pool ${JDBC_CONNECTION_POOL_NAME}