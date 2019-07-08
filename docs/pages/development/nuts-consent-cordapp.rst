.. _nuts-consent-cordapp-development:

Nuts consent cordapp development
################################

.. marker-for-readme

The consent cordapp is written in Kotlin and can be build by Gradle.

Dependencies
************

Since the consent cordapp depends on Corda, Java 1.8 is needed. For the Oracle sdk, this means that your version needs to be > 1.8 update 151.
This can give problems on several linux distro's. In that case use the latest OpenJDK 1.8.

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

The ``nutsfoundation/nuts-consent-cordapp:latest-dev`` docker image can be used to run 3 nodes locally. Checkout :ref:`nuts-network-local-development-docker` for setting up a complete environment with ``docker-compose``.

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

.. notes::

    It seems signing require Oracles JVM! So openjdk won't work.
