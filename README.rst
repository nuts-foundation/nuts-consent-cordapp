##############
nuts-discovery
##############

Discovery Consent Cordapp by the Nuts foundation for distributing Consent records across nodes.

.. image:: https://travis-ci.org/nuts-foundation/nuts-consent-cordapp.svg?branch=master
    :target: https://travis-ci.org/nuts-foundation/nuts-consent-cordapp
    :alt: Build Status

.. image:: https://readthedocs.org/projects/nuts-consent-cordapp/badge/?version=latest
    :target: https://nuts-documentation.readthedocs.io/projects/nuts-consent-cordapp/en/latest/?badge=latest
    :alt: Documentation Status

.. image:: https://codecov.io/gh/nuts-foundation/nuts-consent-cordapp/branch/master/graph/badge.svg
    :target: https://codecov.io/gh/nuts-foundation/nuts-consent-cordapp

.. inclusion-marker-for-contribution

Setup
-----

You'll need the sphinx jsson-schema plugin for generating the documentation:

.. sourcecode:: shell

    pip install sphinx-jsonschema

todo

Release
-------

Both the flows and contract libs are published to maven central (through OSS Sonatype). Before you can release and sign the jars, you need the following things:

- a valid gpg setup
- a published gpg key
- a sonatype account linked to nl.nuts

You can release libraries through:

.. sourcecode:: shell

    ./gradlew uploadArchives

Then go to https://oss.sonatype.org and *close* and *release* the libs. More info can be found on https://central.sonatype.org/pages/releasing-the-deployment.html.
