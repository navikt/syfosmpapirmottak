package no.nav.syfo

enum class QuestionGroup(val spmGruppeId: String, val spmGruppeTekst: String, val spmsvar: List<QuestionId>) {
    GROUP_6_1("6.1", "Utdypende opplysninger ved 4,12 og 28 uker ved visse diagnose", listOf(QuestionId.ID_6_1_1, QuestionId.ID_6_1_2, QuestionId.ID_6_1_3, QuestionId.ID_6_1_4, QuestionId.ID_6_1_5)),
    GROUP_6_2("6.2", "Utdypende opplysninger ved 8,17 og 39 uker", listOf(QuestionId.ID_6_2_1, QuestionId.ID_6_2_2, QuestionId.ID_6_2_3, QuestionId.ID_6_2_4)),
    GROUP_6_3("6.3", "Opplysninger ved vurdering av aktivitetskravet", listOf(QuestionId.ID_6_3_1, QuestionId.ID_6_3_2)),
    GROUP_6_4("6.4", "Helseopplysninger ved 17 uker", listOf(QuestionId.ID_6_4_1, QuestionId.ID_6_4_2, QuestionId.ID_6_4_3)),
    GROUP_6_5("6.5", "Utdypende opplysninger ved 39 uker", listOf(QuestionId.ID_6_5_1, QuestionId.ID_6_5_2, QuestionId.ID_6_5_3, QuestionId.ID_6_5_4)),
    GROUP_6_6("6.6", "Helseopplysninger dersom pasienten søker om AAP", listOf(QuestionId.ID_6_6_1, QuestionId.ID_6_6_2, QuestionId.ID_6_6_3))
}
