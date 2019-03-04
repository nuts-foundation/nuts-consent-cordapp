.. _nuts-consent-cordapp-overview:

Overview
========

The key concepts of Corda are States, Contracts, Transactions and Flows. The chapters below give a high level overview of how they are related. More information can be found at `The corda documentation <https://docs.corda.net/key-concepts.html>`_.

States
------

A Corda state represents a current state of a particular record. Everything in Corda is immutable so states can only linked together by referencing the previous state. States are stored in the vault together with any attachments. Corda has different types of states, within Nuts we use be the `LinearState <https://docs.corda.net/api-states.html?highlight=linearstate#linearstate>`_. A *LinearState* has an *externalId* that never changes, this will be the link Nuts needs to connect encrypted records to real patient records. The *externalId* will be created by the Party that creates the *LinearState*. This is the only mechanism to prevent duplicate entries and therefore must use some sort of consistent hashing algorithm like *HMAC_256* using the private key of the organisation.

.. important::

    The Nuts Corda Consent model represents *Triples*:
        * The care provider responsible for storing the data (the custodian)
        * The patient
        * The care provider that is granted access (the actor)

Together with states Nuts also stores attachments. The attachments hold the actual data and are encrypted with a symmetric key. The symmetric key can only be accessed by the organisations that are involved in the *triple*. To ensure privacy, the symmetric key is encrypted with the public key of the involved organisations.

.. note::

    Next to the two involved care providers (custodian and actor), the PGO (Personal Health Environment) can be an involved party as well. When this is the case, extra attachments will be present containing the patient identifier for the PGO. The attachments that reference the official patient identifier (BSN) will be `hidden <https://docs.corda.net/key-concepts-tearoffs.html>`_ for the PGO.

More information on the structure of the attachments can be read in the :ref:`nuts-consent-cordapp-model` chapter.

Contracts
---------

Contracts in Corda are the pieces of software that check state transitions. Given a particular action a state will be consumed and a new output state will be created. A contract makes sure this operation is atomic across all participating parties. Since Nuts consent states only have an *externalId* and attachments, the contracts will mainly look at the attachment data to make sure that certain constraints at the meta level are correct. The *Nuts Consent Cordapp* will also use an `Corda Oracle <https://docs.corda.net/key-concepts-oracles.html>`_ to get confirmation on the consent data from *service space* (Under architectural construction).

Flows
-----

`Flows <https://docs.corda.net/key-concepts-flows.html>`_ are the main pieces of logic to create or update states. They are basically a step-by-step description on information gathering, sending data to other parties and finalising the transaction. Flows are distinguished in to sending and receiving flows and can contain any number of sub-flows. The sub-flows allow for reuse of functionality. The current supported flows can be found in the :ref:`nuts-consent-cordapp-flows` chapter.

Transactions
------------

A Corda transaction is the process were input states are transformed to output states according to a flow. All involved parties will sign the transaction if it adheres to the contract associated with the transaction. When all parties agree, a `notary <https://docs.corda.net/key-concepts-notaries.html>`_ will sign the transaction making it final. The parties will then proceed to update the changes in the `vault <https://docs.corda.net/vault.html>`_. No custom transaction logic is possible and this is entirely handled by Corda.