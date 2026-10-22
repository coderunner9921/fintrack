
package com.fintrack.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/status")
    public Map<String, String> getAdminStatus() {
        return Map.of(
                "status", "ok",
                "message", "Admin access verified"
        );
    }
}
