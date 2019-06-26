.. _nuts-consent-cordapp-configuration:

Nuts consent cordapp configuration
##################################

The basic node.conf inside the Cordap base directory should look similar like this:

.. code-block:: yaml

    myLegalName="O=Nuts,C=NL,L=Groenlo,CN=nuts_corda_development_dahmer"
    emailAddress="info@nuts.nl"
    devMode=false
    devModeOptions {
      allowCompatibilityZone = true
    }
    networkServices {
        doormanURL = "http://localhost:8080"
        networkMapURL = "http://localhost:8080"
    }
    p2pAddress="localhost:17886"
    rpcSettings {
        address="localhost:11003"
        adminAddress="localhost:11043"
    }
    rpcUsers=[]



Additional info
***************

See https://docs.corda.net/corda-configuration-file.html

