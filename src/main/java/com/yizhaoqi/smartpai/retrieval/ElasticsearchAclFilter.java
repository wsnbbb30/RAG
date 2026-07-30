package com.yizhaoqi.smartpai.retrieval;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yizhaoqi.smartpai.security.AccessScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 新版 ES 索引的唯一 ACL 查询构造器。
 *
 * <p>必须同时传给 BM25 的 bool.filter 和 KNN 的 knn.filter；只放在顶层 query
 * 会让无权文档参与近邻竞争，既有泄漏风险也会降低有权证据召回。</p>
 */
@Component
public class ElasticsearchAclFilter {

    public Query authorizedChunks(RetrievalContext context) {
        AccessScope scope = context.accessScope();
        return Query.of(query -> query.bool(root -> {
            // 仅检索真正可回答的子块，PARENT 只作为后续证据回填上下文使用。
            root.filter(filter -> filter.terms(terms -> terms.field("chunkType")
                    .terms(values -> values.value(List.of(FieldValue.of("TEXT"), FieldValue.of("TABLE"))))));
            addHighConfidenceMetadataFilters(root, context.filters());

            if (scope.isPublicOnly()) {
                root.filter(filter -> filter.term(term -> term.field("isPublic").value(true)));
                return root;
            }

            root.filter(filter -> filter.bool(acl -> {
                acl.should(should -> should.term(term -> term.field("isPublic").value(true)));
                acl.should(should -> should.term(term -> term.field("ownerUserId").value(scope.getUserDbId())));
                if (!scope.getOrgTags().isEmpty()) {
                    acl.should(should -> should.terms(terms -> terms.field("orgTag")
                            .terms(values -> values.value(scope.getOrgTags().stream().map(FieldValue::of).toList()))));
                }
                return acl.minimumShouldMatch("1");
            }));
            return root;
        }));
    }

    /**
     * 仅追加已明确提取的 metadata 条件。该方法没有“猜测公司名”的分支，
     * 这是为了避免金融简称、年份比较问题被错误过滤导致关键证据漏召回。
     */
    private void addHighConfidenceMetadataFilters(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder root,
                                                  QueryFilter filters) {
        if (filters.stockCode() != null) {
            root.filter(filter -> filter.term(term -> term.field("stockCode").value(filters.stockCode())));
        }
        if (filters.fiscalYear() != null) {
            root.filter(filter -> filter.term(term -> term.field("fiscalYear").value(filters.fiscalYear())));
        }
        if (filters.reportType() != null) {
            root.filter(filter -> filter.term(term -> term.field("reportType").value(filters.reportType())));
        }
    }
}
