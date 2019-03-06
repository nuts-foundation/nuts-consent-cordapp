.. _nuts-consent-cordapp:

Nuts Consent Cordapp
====================

The *Nuts Consent Cordapp* (What is a Cordapp?: https://docs.corda.net/cordapp-overview.html) is responsible for creating the decentralised state of consent.
It is essentially the main source for the Nuts consent records and resides in *Nuts space* (:ref:`High level architecture`).
The :ref:`nuts-consent-cordapp-model` therefore consists mainly of encrypted data. Validation of any data specific constraints will be delegated to *Service space* during a Corda transaction.

The basics are described in :ref:`nuts-consent-cordapp-overview` and are a good place to start.

Back to main documentation: :ref:`nuts-documentation`

.. toctree::
    :maxdepth: 2
    :caption: Contents:
    :glob:

    pages/overview
    pages/*
