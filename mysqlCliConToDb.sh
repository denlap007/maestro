#!/bin/bash
docker run -it --link data:mysqlCli2 --rm mysql sh -c 'exec mysql -h"$HOST_IP" -P"$DB_PORT" -uroot -p"$DB_PASS"'
[dio@fed22-ssd ~]$ docker run -it --link data:mysql --rm mysql sh -c 'exec mysql -h172.17.0.4 -P3306 -uroot -proot'