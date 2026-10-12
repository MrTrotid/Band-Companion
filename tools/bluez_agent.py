"""In-process BlueZ Bluetooth Agent for Linux.

Implements the org.bluez.Agent1 D-Bus interface using dbus-fast to handle
pairing authentication requests (such as RequestConfirmation for Passkey/Numeric Comparison)
without relying on external interactive prompts.
"""

import logging
from typing import Optional
from dbus_fast import BusType, Variant
from dbus_fast.aio import MessageBus
from dbus_fast.service import ServiceInterface, method

logger = logging.getLogger("BlueZAgent")

AGENT_PATH = "/org/bandcompanion/agent"
AGENT_INTERFACE = "org.bluez.Agent1"
AGENT_MANAGER_INTERFACE = "org.bluez.AgentManager1"


class BlueZPairingAgent(ServiceInterface):
    def __init__(self):
        super().__init__(AGENT_INTERFACE)

    @method()
    def Release(self):
        logger.debug("Agent released by BlueZ")

    @method()
    def RequestPinCode(self, device: "o") -> "s":
        logger.info("RequestPinCode for device %s, returning '0000'", device)
        return "0000"

    @method()
    def DisplayPinCode(self, device: "o", pincode: "s"):
        logger.info("DisplayPinCode for device %s: %s", device, pincode)

    @method()
    def RequestPasskey(self, device: "o") -> "u":
        logger.info("RequestPasskey for device %s, returning 0", device)
        return 0

    @method()
    def DisplayPasskey(self, device: "o", passkey: "u", entered: "q"):
        logger.info("DisplayPasskey for device %s: %06d (entered: %d)", device, passkey, entered)

    @method()
    def RequestConfirmation(self, device: "o", passkey: "u"):
        logger.info("🎯 Auto-confirming passkey for Band 10: %06d (device: %s)", passkey, device)
        # Empty return accepts the confirmation

    @method()
    def RequestAuthorization(self, device: "o"):
        logger.info("RequestAuthorization auto-approved for device %s", device)

    @method()
    def AuthorizeService(self, device: "o", uuid: "s"):
        logger.info("AuthorizeService auto-approved for device %s, UUID %s", device, uuid)

    @method()
    def Cancel(self):
        logger.debug("Pairing request canceled by BlueZ")


class BlueZAgentManager:
    def __init__(self):
        self.bus: Optional[MessageBus] = None
        self.agent: Optional[BlueZPairingAgent] = None

    async def start(self, capability: str = "KeyboardDisplay"):
        try:
            self.bus = await MessageBus(bus_type=BusType.SYSTEM).connect()
            self.agent = BlueZPairingAgent()
            self.bus.export(AGENT_PATH, self.agent)

            # Get AgentManager1 proxy
            introspection = await self.bus.introspect("org.bluez", "/org/bluez")
            proxy = self.bus.get_proxy_object("org.bluez", "/org/bluez", introspection)
            agent_manager = proxy.get_interface(AGENT_MANAGER_INTERFACE)

            await agent_manager.call_register_agent(AGENT_PATH, capability)
            await agent_manager.call_request_default_agent(AGENT_PATH)
            logger.info("Bluetooth pairing agent registered with BlueZ (%s)", capability)
            return True
        except Exception as e:
            logger.warning("Could not register BlueZ pairing agent over D-Bus: %s", e)
            return False

    async def stop(self):
        if self.bus:
            try:
                introspection = await self.bus.introspect("org.bluez", "/org/bluez")
                proxy = self.bus.get_proxy_object("org.bluez", "/org/bluez", introspection)
                agent_manager = proxy.get_interface(AGENT_MANAGER_INTERFACE)
                await agent_manager.call_unregister_agent(AGENT_PATH)
            except Exception:
                pass
            self.bus.disconnect()
            self.bus = None
