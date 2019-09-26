package com.example.server

import com.example.flow.CastVoteInitiator
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.vaultQueryBy
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.example.state.VotingrState
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest

//val SERVICE_NAMES = listOf("Notary", "Network Map Service", "Oracle")


/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    @GetMapping(value = "/templateendpoint", produces = arrayOf("text/plain"))
    private fun templateendpoint(): String {
        return "Define an endpoint here."
    }

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun whoami() = mapOf("me" to myLegalName)


    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (listOf("Notary", "Network Map Service", "Oracle") + myLegalName.organisation) })
    }

    /**
     * Displays all Votes Ihat exist in the node's vault.
     */
    @GetMapping(value = [ "votes" ], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVotes() : ResponseEntity<List<StateAndRef<VotingrState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<VotingrState>().states)
    }

    @PostMapping(value = [ "post-vote" ])
    fun castVote(request: HttpServletRequest): ResponseEntity<String> {
        //val candidateName = request.getParameter("candidateName").toInt()
        val candidateName = "Maddfd"
        val partyName = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=MegaCorp 1,L=New York,C=US"))!!
//        if (candidateName <= 0 ) {
//            return ResponseEntity.badRequest().body("Query parameter 'candidateName' must be non-negative.\n")
//        }
        //val otherParty = proxy.wellKnownPartyFromX500Name(partyX500Name) ?: return ResponseEntity.badRequest().body("Party named $partyName cannot be found.\n")

        return try {
            val signedTx = proxy.startTrackedFlow(::CastVoteInitiator, partyName, candidateName, 1).returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }
}
