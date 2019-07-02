.. _nuts-consent-cordapp-development:

Nuts consent cordapp development
################################

.. marker-for-readme

The discovery service is written in Kotlin and can be build by Gradle.

Dependencies
************

Since the discovery service depends on Corda, Java 1.8 is needed. For the Oracle sdk, this means that your version needs to be > 1.8 update 151.
This can give problems on several linux distro's. In that case use the latest OpenJDK sdk 1.8.

The project is build with Gradle. A gradle wrapper is present in the project.

Running tests
*************

Tests can be run by executing

.. code-block:: shell

    ./gradlew test

Building
********

Jars can be build by executing

.. code-block:: shell

    ./gradlew jar

Docker
******

To build locally

.. code-block:: shell

    docker build . -f docker/Dockerfile-dev

The ``nutsfoundation/nuts-consent-cordapp:latest-dev`` docker image can be used to run 3 nodes locally. It's important to mount the different files from ``docker/nodes/x``

.. code-block:: shell

    docker run -it -p 7886:7886 -p 7887:7887 -p 7888:7888 -p 2222:2222 \
        --network=nuts --name=notary
        -v /home/user/nuts-consent-cordapp/docker/nodes/notary/node.conf:/opt/nuts/node.conf \
        -v /home/user/nuts-consent-cordapp/docker/nodes/notary/certificates:/opt/nuts/certificates \
        --rm
        nutsfoundation/nuts-consent-cordapp:latest-dev

The ports need to be changed for the other two containers. Port ``7886`` is the main port for p2p traffic. ``7887`` is used for rpc calls and ``2222`` is used to ssh into the node. Change the mounted paths to reflect your machine configuration. the ``--name`` param should match the directory name from which ``node.conf`` is mounted.

This image requires the ``nutsfoundation/nuts-discovery`` image to be running in a container exposed at ``8080``.

If you haven't created a docker network yet, create one to to able to connect the containers together:

.. code-block:: shell

    docker network create -d bridge nuts

README
******

The readme is auto-generated from a template and uses the documentation to fill in the blanks.

.. code-block:: shell

    ./generate_readme.sh

Documentation
*************

To generate the documentation, you'll need python3, sphinx and a bunch of other stuff. See :ref:`nuts-documentation-development-documentation`
The documentation can be build by running

.. code-block:: shell

    /docs $ make html

The resulting html will be available from ``docs/_build/html/index.html``

Release
*******

Both the flows and contract libs are published to maven central (through OSS Sonatype). Before you can release and sign the jars, you need the following things:

- a valid gpg setup
- a published gpg key
- a sonatype account linked to nl.nuts

You can release libraries through:

.. sourcecode:: shell

    ./gradlew uploadArchives

Then go to https://oss.sonatype.org and *close* and *release* the libs. More info can be found on https://central.sonatype.org/pages/releasing-the-deployment.html.
