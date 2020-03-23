.. _nuts-consent-cordapp-monitoring:

Nuts consent cordapp monitoring
###############################

The Corda node in itself offers quite some monitoring options. See: https://docs.corda.net/node-administration.html#monitoring-your-node.

Docker monitoring
*****************

A docker `HEALTHCHECK` is available on the image, it runs `netstat -an | grep 7886`.
This can be connected to your favourite monitoring software. Output can be checked by using `docker inspect --format='{{json .State.Health}}' container`
