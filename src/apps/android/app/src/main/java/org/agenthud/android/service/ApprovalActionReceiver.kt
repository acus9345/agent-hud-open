package org.agenthud.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.agenthud.android.AgentHudApp

class ApprovalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECIDE) return
        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID)?.takeIf { it.isNotBlank() } ?: return
        val decisionKind = intent.getStringExtra(EXTRA_DECISION_KIND)?.takeIf { it.isNotBlank() } ?: return
        val app = context.applicationContext as? AgentHudApp ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.answerApproval(
                    approvalId = approvalId,
                    decision = buildJsonObject {
                        put("kind", decisionKind)
                        if (decisionKind == "approve_plan") put("acceptEdits", true)
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECIDE = "org.agenthud.android.ACTION_APPROVAL_DECIDE"
        const val EXTRA_APPROVAL_ID = "approval_id"
        const val EXTRA_DECISION_KIND = "decision_kind"
    }
}
