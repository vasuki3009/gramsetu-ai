package com.gramsetu.config;

import com.gramsetu.model.DialectWord;
import com.gramsetu.repository.DialectWordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the "render" profile (in-memory H2 in MySQL mode) and checks that data.sql seeded correctly,
 * above all that the Tamil text was read as UTF-8. A wrong charset turns it into '?' or mojibake,
 * which would silently break dialect detection.
 */
@DataJpaTest
@ActiveProfiles("render")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // keep the profile's own H2 datasource
class RenderProfileSeedTest {

    private static final String KNOWN_WORD = "என்ன பண்ணலாம்"; // seeded as KONGU in data.sql

    @Autowired private DialectWordRepository repo;

    @Test
    void dialectWordsAreSeededAndTamilIsStoredIntact() throws Exception {
        List<DialectWord> words = repo.findAll();

        assertThat(words).hasSize(seededDialectWordCount());
        assertThat(words).anySatisfy(w -> {
            assertThat(w.getWord()).isEqualTo(KNOWN_WORD);
            assertThat(w.getDialect()).isEqualTo("KONGU");
        });
        assertThat(words).noneMatch(w -> w.getWord().contains("?") || w.getWord().contains("�"));
    }

    /** Counts the rows of the dialect_words INSERT in data.sql (read as UTF-8), so no number is hard-coded. */
    private static int seededDialectWordCount() throws Exception {
        String sql = new ClassPathResource("data.sql").getContentAsString(StandardCharsets.UTF_8);
        String section = sql.substring(sql.indexOf("INSERT IGNORE INTO dialect_words"),
                                       sql.indexOf("INSERT IGNORE INTO crop_advisories"));
        Matcher m = Pattern.compile("\\('[^']+','(KONGU|MADURAI|NELLAI)',").matcher(section);
        int rows = 0;
        while (m.find()) rows++;
        return rows;
    }
}
