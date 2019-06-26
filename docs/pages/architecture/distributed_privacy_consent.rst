.. _nuts-consent-cordapp-architecture-dpc:

Distributed Privacy Consent
===========================

Throughout the documentation we'll be talking about *DPC* records. A *DPC* record is an unique record representing multiple consent records and parties with the same:

    * **Custodian** The care organisation responsible for managing the data. This is typically the party with which the patient has an agreement for providing care.
    * **Subject** The patient, client or civilian. Any data transferred between parties is about this subject.
    * **Actor** The care provider that is granted access. The actor needs the data in order to provide better health care.

Reasoning behind DPC
--------------------

The main reasoning behind a *DPC* record is that a care provider (actor) wants to access some piece of data about a patient (subject) from a different care organisation (custodian).
In the Nuts network model, the software from the actor will connect directly to the API from the custodian and pass along the subject in the request.
The actor uses an Irma signature for authentication and also passes along the identifier of the custodian.
With this information the authenticating API endpoint has enough information to consult the consent records and make a decision.

.. note::

    In a SaaS environment, multiple custodians can be accessible through the same API endpoint. Passing along some identifier for the custodian is then needed.

A *DPC* record also maps very well to the health care process itself. Asking a subject if information about him may be transferred to other software systems is ridicules.
Asking if a particular care provider can get access on the other hand is what is currently done today.

.. note::

    A General Practitioner might ask the following question during a consult: "Is it ok if the pharmacist can access medical information from this office?"

It would be an option to exclude the actor from a *DPC* record (and make it a Subject Domain Link) and just store everything about the subject in a single record with lots of attachments.
This would mean though that all care organisations and/or care providers are involved parties in any transaction.
It would no longer be possible to isolate a certain part of subject's health care network, which is unacceptable based on the manifest of Nuts.

.. note::

    Without the actor in a *DPC* record, a mental health care professional would be an equal party to, for example, the home care organisation.
    Both organisations would be able to 'see' each other as parties where active consent is registered.
    Even if the home care organisation does not have consent to access the data from the mental health care organisation it would still know some care is being given.
    Given some extremely specialised care organisations, it would then be possible to determine what kind of care the subject is receiving.

FHIR mapping
------------

Consent in Nuts is stored as encrypted FHIR consent records. Next to the three parties involved in a *DPC* record, the FHIR consent record also mentions:

    * A witness, the person who has witnessed the consent agreement.
    * The Consentor, the person who has given consent on behalf of the subject.

In many cases, the witness will be a care professional who has witnessed a verbal agreement or uploaded the wet-autograph as a user of the system of the custodian.
In the case of a digital signature, the witness is a less crucial part of the consent record.
The consentor can either be the subject itself or anybody that has a mandate to give consent on behalf of the patient.
These two parties are not present in a *DPC* record since they only say something about the validity of the record and not the uniqueness.

PGO and implicit access
-----------------------

The above sections all use the professional to professional data transfer as subject. The patient himself via his PGO (Personal Health care Environment) is also part of a *DPC* record.
The PGO though is not an actor and not a custodian, it represents the subject. So given a *Triple*, a PGO can be added as party to the consent record when it represents the patient.
Any request a custodian API receives from a PGO user must therefore check the consent records for the patient identifier and not the actor.
This way the patient can view and change the consent records as a party directly involved.
For a PGO to view any medical data Nuts supports the notion of a :ref:`Subject Domain Link <nuts-consent-cordapp-architecture-sdl>`. Viewing any medical data in a PGO must be possible regardless of any consent records being available. A patient must be able to view their own data even without any consent registered.
Therefore the *Subject Domain Link* is a different structure than a *DPC* record.
