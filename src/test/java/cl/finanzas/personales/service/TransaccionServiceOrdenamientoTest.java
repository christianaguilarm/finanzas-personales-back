package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.mapper.TransaccionMapper;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.ComercioRepository;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.TagRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceOrdenamientoTest {

    @Mock
    private TransaccionRepository transaccionRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private ComercioRepository comercioRepository;
    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TransaccionMapper mapper;

    @InjectMocks
    private TransaccionService transaccionService;

    @Test
    @DisplayName("Traduce la columna cuotas de la UI a la propiedad cuotaActual")
    void traduceCuotasACuotaActual() {
        Pageable enviada = capturarOrden(
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("cuotas")))
        );

        Sort orden = enviada.getSort();
        assertThat(orden.isSorted()).isTrue();
        assertThat(orden.getOrderFor("cuotaActual")).isNotNull();
        assertThat(orden.getOrderFor("cuotaActual").isAscending()).isTrue();
    }

    @Test
    @DisplayName("Ordena por nombre de categoría y de cuenta en vez de por el id")
    void traduceCategoriasYCuentasPorNombre() {
        Pageable enviada = capturarOrden(
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("categoria"), Sort.Order.asc("cuenta")))
        );

        assertThat(enviada.getSort().getOrderFor("categoria.nombre").isDescending()).isTrue();
        assertThat(enviada.getSort().getOrderFor("cuenta.nombre").isAscending()).isTrue();
    }

    @Test
    @DisplayName("Acepta las columnas que sí existen en Transaccion sin traducirlas")
    void conservaLasPropiedadesValidas() {
        Pageable enviada = capturarOrden(
                PageRequest.of(2, 25, Sort.by(Sort.Order.desc("monto"), Sort.Order.asc("fecha")))
        );

        assertThat(enviada.getPageNumber()).isEqualTo(2);
        assertThat(enviada.getPageSize()).isEqualTo(25);
        assertThat(enviada.getSort().getOrderFor("monto").isDescending()).isTrue();
        assertThat(enviada.getSort().getOrderFor("fecha").isAscending()).isTrue();
    }

    @Test
    @DisplayName("Rechaza con 400 una columna de ordenamiento inexistente")
    void rechazaPropiedadInexistente() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("columnaFalsa")));

        assertThatThrownBy(() -> transaccionService.obtenerTransaccionesPorUsuario(
                1L, new TransaccionFiltroQuery(), pageable))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se puede ordenar por 'columnaFalsa'");
    }

    @Test
    @DisplayName("No toca la paginación cuando no viene ordenamiento")
    void conservaPageableSinOrden() {
        Pageable enviada = capturarOrden(PageRequest.of(1, 20));

        assertThat(enviada.getPageNumber()).isEqualTo(1);
        assertThat(enviada.getPageSize()).isEqualTo(20);
        assertThat(enviada.getSort().isUnsorted()).isTrue();
    }

    private Pageable capturarOrden(Pageable pageable) {
        when(transaccionRepository.findAll(
                ArgumentMatchers.<Specification<cl.finanzas.personales.model.Transaccion>>any(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        transaccionService.obtenerTransaccionesPorUsuario(1L, new TransaccionFiltroQuery(), pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transaccionRepository).findAll(
                ArgumentMatchers.<Specification<cl.finanzas.personales.model.Transaccion>>any(),
                captor.capture());
        return captor.getValue();
    }
}
