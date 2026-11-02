package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.ComercioRequest;
import cl.finanzas.personales.model.Comercio;
import cl.finanzas.personales.repository.ComercioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComercioServiceTest {
    @Mock private ComercioRepository repository;
    @InjectMocks private ComercioService service;

    @Test
    void listaSoloLosComerciosDelUsuario() {
        Comercio comercio = new Comercio();
        comercio.setId(3L);
        comercio.setNombre("Almacen");
        when(repository.findByUserIdOrderByNombreAsc(7L)).thenReturn(List.of(comercio));
        assertThat(service.obtenerComercios(7L)).singleElement()
                .satisfies(c -> assertThat(c.nombre()).isEqualTo("Almacen"));
        verify(repository).findByUserIdOrderByNombreAsc(7L);
    }

    @Test
    void creaConElUsuarioActualYNormalizaCampos() {
        when(repository.save(any())).thenAnswer(invocation -> {
            Comercio comercio = invocation.getArgument(0);
            assertThat(comercio.getUser().getId()).isEqualTo(7L);
            comercio.setId(8L);
            return comercio;
        });
        var respuesta = service.crearComercio(7L, new ComercioRequest("  Almacen  ", "  "));
        assertThat(respuesta.id()).isEqualTo(8L);
        assertThat(respuesta.nombre()).isEqualTo("Almacen");
        assertThat(respuesta.rubro()).isNull();
    }

    @Test
    void listaVaciaNoDevuelveNull() {
        when(repository.findByUserIdOrderByNombreAsc(7L)).thenReturn(List.of());
        assertThat(service.obtenerComercios(7L)).isEmpty();
    }
}
