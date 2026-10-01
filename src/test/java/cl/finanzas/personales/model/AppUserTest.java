package cl.finanzas.personales.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppUser tiene @Builder con inicializadores de campo. Sin @Builder.Default, Lombok los descarta:
 * el builder devolvia null en campos NOT NULL y reventaba al insertar.
 */
class AppUserTest {

    @Test
    @DisplayName("el builder deja creadoEn informado")
    void elBuilderPoneCreadoEn() {
        assertThat(AppUser.builder().build().getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("el builder deja cuentas como un Set vacio, no null")
    void elBuilderPoneCuentasVacias() {
        assertThat(AppUser.builder().build().getCuentas()).isEmpty();
    }

    @Test
    @DisplayName("el builder deja el resto en null y eso es correcto")
    void elBuilderDejaLoDemasEnNull() {
        AppUser usuario = AppUser.builder().build();

        assertThat(usuario.getAuth0Sub()).isNull();
        assertThat(usuario.getEmail()).isNull();
        assertThat(usuario.getNombre()).isNull();
        assertThat(usuario.getPasswordHash()).isNull(); // nullable: ya no hay login local
    }

    @Test
    @DisplayName("new AppUser() tambien inicializa creadoEn y cuentas")
    void elConstructorVacioTambienInicializaLosDefaults() {
        AppUser usuario = new AppUser();

        assertThat(usuario.getCreadoEn()).isNotNull();
        assertThat(usuario.getCuentas()).isEmpty();
    }
}