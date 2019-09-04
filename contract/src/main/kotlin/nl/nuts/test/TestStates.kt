/*
 *     Nuts consent cordapp
 *     Copyright (C) 2019 Nuts community
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package nl.nuts.test

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 * Genesis and Active are modelled as two different states for simplification.
 */

@CordaSerializable
@BelongsToContract(TestContract::class)
data class GenesisState(
        val uuid: UniqueIdentifier,
        val parties: Set<Party> = emptySet()) : LinearState {

    override val linearId: UniqueIdentifier get() = uuid
    override val participants: List<Party> get() = parties.toList()

    override fun toString() = uuid.toString()
}

@CordaSerializable
@BelongsToContract(TestContract::class)
data class AddState(
        val uuid: UniqueIdentifier,
        val main: UniqueIdentifier,
        val parties: Set<Party> = emptySet()) : LinearState {

    override val linearId: UniqueIdentifier get() = uuid
    override val participants: List<Party> get() = parties.toList()

    override fun toString() = uuid.toString()
}

@CordaSerializable
@BelongsToContract(TestContract::class)
data class ActiveState(
        val uuid: UniqueIdentifier,
        val parties: Set<Party> = emptySet()) : LinearState {

    override val linearId: UniqueIdentifier get() = uuid
    override val participants: List<Party> get() = parties.toList()

    override fun toString() = uuid.toString()
}