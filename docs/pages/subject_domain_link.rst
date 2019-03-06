.. _nuts-consent-cordapp-sdl:

Subject Domain Link
===================

Next to a :ref:`DPC record <nuts-consent-cordapp-dpc>`, Nuts also has a *Subject Domain Link* consent record.
This record represents the *link* a PGO (Personal Health care Environment) has to the medical domain.
A *Subject Domain Link* represents the following parties:

    * **Custodian** The care organisation responsible for managing the data. This is typically the party with which the patient has an agreement for providing care.
    * **Subject** The patient, client or civilian. Any data transferred between parties is about this subject.

Isolation
---------

A *Subject Domain Link* is created every time when a user/patient makes a link with the medical domain by disclosing their BSN at the inclusion website of a care organisation.
This will let the *service space* of that care organisation know that a particular PGO user may be linked to a BSN record.
The uniqueId for the *LinearState* will be created as an HMAC_256 of BSN and PGO user id.
The user can choose to make this *link* private or *viral*. When private, the user can only access data for that particular care organisation.
Other care organisation have to be added in the same way by creating new *links*.

FHIR mapping
------------

The *Subject Domain Link* also uses an encrypted FHIR consent record as data carrier. The policy will most likely be simpler since it'll only target the patient role itself.

Subject Domain Links going viral
--------------------------------

When a user makes a *link*, it should be able to choose it to be able to go viral.
This means that the care organisation is allowed to send the consent record to other parties that are involved in the care of the patient.
The parties receiving the consent may then also do the same. This 'register once, see all' principle will greatly reduce the burden for the user.
When the viral option is chosen, two consent records are attached to the *Subject Domain Link*: the actual consent from the subject to the custodian and the consent records allowing custodians to redistribute the first record.
The records are limited for care organisations only. This would block any Nuts node from sending it to a non-medical Nuts node.

.. todo:

    Describe Subject Domain Link, does unique ID propagation work the same way?
    What happens if the first giving Care Provider disappears??? << should not matter?