package com.djorka.incidentops.service;

import com.djorka.incidentops.model.PreferredLanguage;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TransactionalEmailTemplateRenderer {

    public RenderedEmail render(TransactionalEmailMessage message) {
        return switch (message.type()) {
            case WELCOME -> welcome(message.language(), message.payload());
            case NEW_SIGN_IN -> newSignIn(message.language(), message.payload());
            case PASSWORD_CHANGED -> passwordChanged(message.language(), message.payload());
            case PASSWORD_RESET -> passwordReset(message.language(), message.payload());
            case PASSWORD_RESET_COMPLETED -> passwordResetCompleted(message.language(), message.payload());
        };
    }

    private RenderedEmail welcome(PreferredLanguage language, Map<String, String> payload) {
        boolean es = language == PreferredLanguage.ES;
        String name = value(payload, "name");
        String role = roleLabel(value(payload, "role"), language);
        String subject = es ? "Bienvenido a IncidentOps" : "Welcome to IncidentOps";
        String title = es ? "Tu cuenta está lista" : "Your account is ready";
        String body = es
                ? "Hola " + name + ". Tu cuenta se creó correctamente y ya puedes acceder a IncidentOps. Rol actual: " + role + "."
                : "Hello " + name + ". Your account was created successfully and you can now access IncidentOps. Current role: " + role + ".";
        return render(subject, title, List.of(body), null, null, language);
    }

    private RenderedEmail newSignIn(PreferredLanguage language, Map<String, String> payload) {
        boolean es = language == PreferredLanguage.ES;
        String subject = es ? "Nuevo inicio de sesión en IncidentOps" : "New sign-in to IncidentOps";
        String title = es ? "Detectamos un nuevo inicio de sesión" : "We detected a new sign-in";
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(es
                ? "Se inició sesión en tu cuenta. Fecha y hora (UTC): " + value(payload, "occurredAt") + "."
                : "Your account was signed in to. Date and time (UTC): " + value(payload, "occurredAt") + ".");
        addOptionalDetail(paragraphs, es ? "IP: " : "IP: ", payload.get("ip"));
        addOptionalDetail(paragraphs, es ? "Navegador: " : "Browser: ", payload.get("userAgent"));
        paragraphs.add(es
                ? "Si fuiste tú, no necesitas hacer nada. Si no reconoces esta actividad, cambia tu contraseña."
                : "If this was you, no action is needed. If you do not recognize this activity, change your password.");
        return render(subject, title, paragraphs, null, null, language);
    }

    private RenderedEmail passwordChanged(PreferredLanguage language, Map<String, String> payload) {
        boolean es = language == PreferredLanguage.ES;
        String subject = es
                ? "Tu contraseña de IncidentOps fue actualizada"
                : "Your IncidentOps password was changed";
        String title = es ? "Contraseña actualizada" : "Password changed";
        String body = es
                ? "La contraseña de tu cuenta se actualizó correctamente. Si no realizaste este cambio, contacta al administrador o soporte."
                : "Your account password was updated successfully. If you did not make this change, contact your administrator or support.";
        return render(subject, title, List.of(body), null, null, language);
    }

    private RenderedEmail passwordReset(PreferredLanguage language, Map<String, String> payload) {
        boolean es = language == PreferredLanguage.ES;
        String subject = es
                ? "Restablece tu contraseña de IncidentOps"
                : "Reset your IncidentOps password";
        String title = es ? "Restablece tu contraseña" : "Reset your password";
        String body = es
                ? "Usa el siguiente enlace para elegir una contraseña nueva. El enlace expira en 20 minutos."
                : "Use the following link to choose a new password. This link expires in 20 minutes.";
        String cta = es ? "Restablecer contraseña" : "Reset password";
        return render(subject, title, List.of(body), cta, value(payload, "resetUrl"), language);
    }

    private RenderedEmail passwordResetCompleted(PreferredLanguage language, Map<String, String> payload) {
        boolean es = language == PreferredLanguage.ES;
        String subject = es
                ? "Tu contraseña de IncidentOps fue restablecida"
                : "Your IncidentOps password was reset";
        String title = es ? "Tu contraseña fue restablecida" : "Your password was reset";
        String body = es
                ? "El restablecimiento terminó correctamente. Si no realizaste esta operación, contacta al administrador o soporte de inmediato."
                : "The reset completed successfully. If you did not perform this action, contact your administrator or support immediately.";
        return render(subject, title, List.of(body), null, null, language);
    }

    private RenderedEmail render(
            String subject,
            String title,
            List<String> paragraphs,
            String ctaLabel,
            String ctaUrl,
            PreferredLanguage language) {
        StringBuilder content = new StringBuilder();
        for (String paragraph : paragraphs) {
            content.append("<p style=\"margin:0 0 16px;color:#334155;font-size:16px;line-height:1.6\">")
                    .append(escape(paragraph))
                    .append("</p>");
        }
        if (ctaLabel != null && ctaUrl != null) {
            content.append("<p style=\"margin:24px 0\"><a href=\"")
                    .append(escape(ctaUrl))
                    .append("\" style=\"display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:600;padding:12px 20px;border-radius:8px\">")
                    .append(escape(ctaLabel))
                    .append("</a></p>");
        }
        String securityFooter = language == PreferredLanguage.ES
                ? "Este es un mensaje de seguridad de IncidentOps. Nunca te pediremos tu contraseña por email."
                : "This is a security message from IncidentOps. We will never ask for your password by email.";
        String html = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>@media(max-width:600px){.card{padding:24px!important}.shell{padding:12px!important}}</style></head>
                <body style="margin:0;background:#f1f5f9;font-family:Arial,sans-serif">
                <div class="shell" style="padding:32px 16px"><div class="card" style="max-width:600px;margin:auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;padding:32px">
                <div style="font-size:20px;font-weight:700;color:#0f172a;margin-bottom:24px">IncidentOps</div>
                <h1 style="margin:0 0 20px;color:#0f172a;font-size:24px;line-height:1.3">%s</h1>%s
                <div style="border-top:1px solid #e2e8f0;margin-top:28px;padding-top:18px;color:#64748b;font-size:13px;line-height:1.5">%s</div>
                </div></div></body></html>
                """.formatted(escape(title), content, escape(securityFooter));

        StringBuilder text = new StringBuilder(title).append("\n\n");
        paragraphs.forEach(paragraph -> text.append(paragraph).append("\n\n"));
        if (ctaLabel != null && ctaUrl != null) {
            text.append(ctaLabel).append(": ").append(ctaUrl).append("\n\n");
        }
        text.append(securityFooter);
        return new RenderedEmail(subject, html, text.toString());
    }

    private void addOptionalDetail(List<String> paragraphs, String label, String value) {
        if (value != null && !value.isBlank()) {
            paragraphs.add(label + value);
        }
    }

    private String roleLabel(String role, PreferredLanguage language) {
        return switch (role) {
            case "ADMIN" -> language == PreferredLanguage.ES ? "Administrador" : "Administrator";
            case "RESPONDER" -> language == PreferredLanguage.ES ? "Respondedor" : "Responder";
            default -> language == PreferredLanguage.ES ? "Observador" : "Viewer";
        };
    }

    private String value(Map<String, String> payload, String key) {
        String value = payload.get(key);
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
