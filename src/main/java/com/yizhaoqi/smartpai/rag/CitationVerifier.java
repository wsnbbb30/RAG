package com.yizhaoqi.smartpai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最小引用协议校验器。
 *
 * <p>回答中的引用必须使用 [E数字]，并且只能指向本轮冻结的 evidence。当前阶段校验标识和页码可定位性；
 * S3 数字事实落库后将在此扩展 quote/数值逐项核验。</p>
 */
@Component
public class CitationVerifier {
    private static final Pattern CITATION = Pattern.compile("\\[(E\\d+)]");

    public CitationVerification verify(String answer, List<Evidence> evidence) {
        Set<String> allowed = new HashSet<>(evidence.stream().map(Evidence::citationId).toList());
        List<String> cited = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        Matcher matcher = CITATION.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            String id = matcher.group(1);
            cited.add(id);
            if (!allowed.contains(id)) invalid.add(id);
        }
        if (!invalid.isEmpty()) return new CitationVerification(false, cited, invalid, "包含本轮证据集外的引用");
        if (!evidence.isEmpty() && cited.isEmpty()) return new CitationVerification(false, cited, invalid, "回答缺少证据引用");
        return new CitationVerification(true, cited, invalid, "");
    }
}
