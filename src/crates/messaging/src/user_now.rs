//! "Send now" from the HUD or a paired phone: the person's words steered
//! into the turn in progress where the client takes that, otherwise queued.
use super::*;

impl Messaging {
    /// "Send now": like [`Self::user_send`] into a session, but steered into
    /// the turn in progress where the client takes that (Codex, DeepSeek
    /// Harness), answering `steered`; otherwise into the session's queue.
    pub fn user_send_now(
        &mut self,
        client: &str,
        session_id: &str,
        text: &str,
        host: &Host<'_>,
        now: i64,
    ) -> Result<Value> {
        let target = UserTarget::Session {
            client: client.to_owned(),
            session_id: session_id.to_owned(),
        };
        self.user_send_as(&target, text, host, now, true)
    }

    /// The person's words into session `id`: "Send now" steers into the turn
    /// in progress where the client takes that; anything else goes into the
    /// session's own queue.
    pub(super) fn send_own(
        &self,
        client: &str,
        id: &str,
        text: &str,
        steer: bool,
    ) -> delivery::Result<delivery::Receipt> {
        let request = |operation| SendRequest {
            provider: provider(client),
            session_id: id.to_owned(),
            request_id: message_id(),
            text: text.to_owned(),
            form: PromptForm::Own,
            operation,
            expected_turn_id: None,
            model: None,
        };
        let steered = (steer && provider(client).takes_steer())
            .then(|| self.clients.send(request(Operation::Steer)))
            .and_then(|sent| sent.ok())
            .filter(|receipt| receipt.state == DeliveryState::Steered);
        match steered {
            Some(receipt) => Ok(receipt),
            None => self.clients.send(request(Operation::Enqueue)),
        }
    }

    /// Whether a refusal of `text` to `client` stands however the session is
    /// woken: the words themselves, or an agent the person turned off.
    pub fn user_refusal_is_final(&mut self, client: &str, text: &str, host: &Host<'_>) -> bool {
        self.preferences = Preferences::read(&host.snapshot["settings"]);
        let language = language(host.snapshot);
        check_text(text, language).is_some()
            || !USER_CLIENTS.contains(&client)
            || self
                .user_client_off(client, host.snapshot, language)
                .is_some()
    }
}
