package com.lingsql.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminSpaControllerTest {

    @Test
    void shouldRedirectAdminEntryToIndex() {
        AdminSpaController controller = new AdminSpaController();

        String viewName = controller.redirectAdminEntry();

        assertEquals("redirect:/admin/index.html", viewName);
    }

    @Test
    void shouldForwardSpaRoutesToAdminIndex() {
        AdminSpaController controller = new AdminSpaController();

        String viewName = controller.forwardAdminSpaRoutes();

        assertEquals("forward:/admin/index.html", viewName);
    }
}

