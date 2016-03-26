#!/bin/bash
set -e

asadmin delete-jdbc-resource jdbc/consult
asadmin delete-jdbc-connection-pool mysql_consult_rootPool