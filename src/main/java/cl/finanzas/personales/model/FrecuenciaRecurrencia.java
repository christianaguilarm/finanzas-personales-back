package cl.finanzas.personales.model;

public enum FrecuenciaRecurrencia {
    MENSUAL(1), TRIMESTRAL(3), CUATRIMESTRAL(4), SEMESTRAL(6), ANUAL(12);

    private final int meses;
    FrecuenciaRecurrencia(int meses) { this.meses = meses; }
    public int getMeses() { return meses; }
}
