package com.foodcore.api;

import com.foodcore.model.Usuario;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Configuración aislada para credenciales demo y PINs en memoria exclusivamente
 * bajo el perfil explícito {@code e2e-test}. Permanece desactivada por defecto en producción.
 */
final class AuthTestBypassConfig {

    static final String PROFILE_E2E_TEST = "e2e-test";
    static final String PROP_SPRING_PROFILES = "spring.profiles.active";
    static final String PROP_FOODCORE_PROFILE = "foodcore.profile";
    static final String ENV_SPRING_PROFILES = "SPRING_PROFILES_ACTIVE";
    static final String ENV_FOODCORE_PROFILE = "FOODCORE_PROFILE";

    private static final Map<Integer, String> PINS_MEMORIA = new ConcurrentHashMap<>(Map.of(
            1, "9999",
            2, "2001",
            3, "1234"
    ));

    private AuthTestBypassConfig() {
    }

    static boolean isE2eTestProfileActive() {
        return isE2eTestProfileActive(System::getenv);
    }

    static boolean isE2eTestProfileActive(Function<String, String> envLookup) {
        String propSpring = System.getProperty(PROP_SPRING_PROFILES);
        String springVal = propSpring != null ? propSpring : envLookup.apply(ENV_SPRING_PROFILES);
        if (contienePerfilE2e(springVal)) {
            return true;
        }
        String propFoodCore = System.getProperty(PROP_FOODCORE_PROFILE);
        String foodCoreVal = propFoodCore != null ? propFoodCore : envLookup.apply(ENV_FOODCORE_PROFILE);
        return contienePerfilE2e(foodCoreVal);
    }

    private static boolean contienePerfilE2e(String rawProfiles) {
        if (rawProfiles == null || rawProfiles.isBlank()) {
            return false;
        }
        for (String token : rawProfiles.split("[,\\s]+")) {
            if (PROFILE_E2E_TEST.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean esCredencialOperativaValida(String user, String pass) {
        if (!isE2eTestProfileActive() || user == null || pass == null) {
            return false;
        }
        String u = user.trim().toLowerCase(Locale.ROOT);
        if ("mozo".equals(u) || "mozo1".equals(u)) {
            return "Mozo2026!".equals(pass) || "123456".equals(pass) || "3001".equals(pass) || "mozo123".equals(pass);
        }
        if ("admin".equals(u)) {
            return "Admin2026!".equals(pass) || "admin123".equals(pass) || "123456".equals(pass) || "1001".equals(pass);
        }
        if ("cajero".equals(u) || "cajero1".equals(u)) {
            return "Cajero2026!".equals(pass) || "123456".equals(pass) || "2001".equals(pass);
        }
        if ("cocinero".equals(u) || "cocina".equals(u) || "cocina1".equals(u)) {
            return "Cocina2026!".equals(pass) || "cocina123".equals(pass);
        }
        return false;
    }

    static Usuario construirUsuarioOperativo(String username) {
        if (!isE2eTestProfileActive()) {
            return null;
        }
        Usuario u = new Usuario();
        if ("admin".equalsIgnoreCase(username)) {
            u.setIdUsuario(1);
            u.setUsername("admin");
            u.setNombreRol("ADMIN");
            u.setNombreEmpleado("Administrador General");
            u.setPin("9999");
            u.setPinHash("$2a$12$JFii7tvJWtK9aSOPGmHdrOA/lBnHzRAhOwQzxHopH9xSCQJBGTNT2");
        } else if ("cajero".equalsIgnoreCase(username) || "cajero1".equalsIgnoreCase(username)) {
            u.setIdUsuario(2);
            u.setUsername("cajero");
            u.setNombreRol("CAJERO");
            u.setNombreEmpleado("Valeria Ramos");
            u.setPin("2001");
            u.setPinHash("$2a$12$2RZFZzZjZXIYB8jWU3JpoO/uo4ga733mPW4enX/V68qyYW0rHXKsa");
        } else if ("cocinero".equalsIgnoreCase(username) || "cocina".equalsIgnoreCase(username) || "cocina1".equalsIgnoreCase(username)) {
            u.setIdUsuario(4);
            u.setUsername("cocinero");
            u.setNombreRol("COCINA");
            u.setNombreEmpleado("Chef Cocina");
            u.setPin("4001");
            u.setPinHash("$2a$12$2RZFZzZjZXIYB8jWU3JpoO/uo4ga733mPW4enX/V68qyYW0rHXKsa");
        } else {
            u.setIdUsuario(3);
            u.setUsername(username != null ? username : "mozo");
            u.setNombreRol("MOZO");
            u.setNombreEmpleado("Mateo Quispe");
            u.setPin("1234");
            u.setPinHash("$2a$12$2RZFZzZjZXIYB8jWU3JpoO/uo4ga733mPW4enX/V68qyYW0rHXKsa");
        }
        u.setActivo(true);
        return u;
    }

    static List<Usuario> listarUsuariosOperativos() {
        if (!isE2eTestProfileActive()) {
            return List.of();
        }
        return List.of(
                construirUsuarioOperativo("admin"),
                construirUsuarioOperativo("mozo"),
                construirUsuarioOperativo("cajero")
        );
    }

    static Usuario autenticarPinMemoria(int idUsuario, String username, String pin) {
        if (!isE2eTestProfileActive() || pin == null) {
            return null;
        }
        int targetId = idUsuario;
        if (targetId <= 0) {
            if ("admin".equalsIgnoreCase(username)) {
                targetId = 1;
            } else if ("cajero".equalsIgnoreCase(username)) {
                targetId = 2;
            } else {
                targetId = 3;
            }
        }
        String pinEsperado = PINS_MEMORIA.getOrDefault(
                targetId,
                targetId == 1 ? "9999" : (targetId == 2 ? "2001" : "1234")
        );
        if (pinEsperado.equals(pin)) {
            String uNom = targetId == 1 ? "admin"
                    : (targetId == 2 ? "cajero" : (username != null && !username.isBlank() ? username : "mozo"));
            return construirUsuarioOperativo(uNom);
        }
        return null;
    }

    static void registrarPinMemoria(int idUsuario, String nuevoPin) {
        if (isE2eTestProfileActive() && nuevoPin != null) {
            PINS_MEMORIA.put(idUsuario, nuevoPin);
        }
    }
}
