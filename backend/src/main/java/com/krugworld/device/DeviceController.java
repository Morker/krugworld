package com.krugworld.device;

import com.krugworld.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The web side of pairing (Settings): make a code, see paired devices, revoke one. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @GetMapping
    public List<DeviceService.DeviceView> list(@AuthenticationPrincipal CurrentUser me) {
        return devices.list(me.id());
    }

    @PostMapping("/pairing-code")
    public DeviceService.PairingCode newCode(@AuthenticationPrincipal CurrentUser me) {
        return devices.newPairingCode(me.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal CurrentUser me, @PathVariable Long id) {
        devices.revoke(me.id(), id);
    }
}
