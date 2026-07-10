-- F4 original seed questions for cold start.
-- These questions are project-created samples, not crawled from commercial question banks.
-- Usage:
--   mysql --default-character-set=utf8mb4 -uroot -proot smart_learning_system < docs/sql/f4_question_bank_seed.sql

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 1, 1, '已知一次函数 y=2x+3，当 x=4 时，y 的值是多少？', '9|10|11|12', '11', '把 x=4 代入 y=2x+3，得 y=8+3=11。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '已知一次函数 y=2x+3，当 x=4 时，y 的值是多少？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 2, 1, '若点 (2,7) 在一次函数 y=kx+1 上，则 k 的值是多少？', '2|3|4|5', '3', '代入点坐标：7=2k+1，2k=6，k=3。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '若点 (2,7) 在一次函数 y=kx+1 上，则 k 的值是多少？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 2, 2, '一次函数 y=-x+5 的图像与 y 轴交点坐标是什么？', '', '(0,5)', 'y 轴上的点 x=0，代入得 y=5，因此交点为 (0,5)。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '一次函数 y=-x+5 的图像与 y 轴交点坐标是什么？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '二次函数', 1, 1, '二次函数 y=x^2 的顶点坐标是？', '(0,0)|(1,0)|(0,1)|(1,1)', '(0,0)', 'y=x^2 是标准抛物线，顶点在原点。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '二次函数 y=x^2 的顶点坐标是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '二次函数', 2, 2, '求二次函数 y=(x-2)^2+1 的最小值。', '', '1', '平方项 (x-2)^2 的最小值为0，所以函数最小值为1。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '求二次函数 y=(x-2)^2+1 的最小值。');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '勾股定理', 1, 1, '直角三角形两条直角边分别为 3 和 4，斜边长是多少？', '5|6|7|8', '5', '由勾股定理 c^2=3^2+4^2=25，c=5。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '直角三角形两条直角边分别为 3 和 4，斜边长是多少？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '英语', '一般现在时', 1, 1, 'She ___ to school by bus every day.', 'go|goes|went|going', 'goes', '主语 She 是第三人称单数，一般现在时动词用 goes。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = 'She ___ to school by bus every day.');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '英语', '一般过去时', 1, 1, 'I ___ a book yesterday.', 'read|reads|reading|will read', 'read', 'yesterday 表示过去，read 的过去式仍写作 read。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = 'I ___ a book yesterday.');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '英语', '情态动词', 2, 1, 'You ___ finish your homework before watching TV.', 'can|must|may|might', 'must', '句意表示必须先完成作业，应选 must。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = 'You ___ finish your homework before watching TV.');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '语文', '病句修改', 1, 1, '下列句子没有语病的一项是？', '通过这次活动，使我明白了合作的重要性。|我们要养成认真听讲的习惯。|他的写作水平明显改善了。|这本书的内容和插图都很美丽。', '我们要养成认真听讲的习惯。', 'A缺主语，C搭配不当，D“内容美丽”搭配不当。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '下列句子没有语病的一项是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '语文', '成语运用', 1, 1, '“他做事总是提前规划，从不临时抱佛脚。”句中“临时抱佛脚”的意思是？', '事先准备充分|平时不准备，事到临头才匆忙应付|做事非常认真|遇事冷静沉着', '平时不准备，事到临头才匆忙应付', '“临时抱佛脚”比喻平时没有准备，临时慌忙应付。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '“他做事总是提前规划，从不临时抱佛脚。”句中“临时抱佛脚”的意思是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '语文', '阅读理解', 2, 2, '阅读短文时，概括段落大意通常应抓住哪些信息？', '', '中心句、关键词、主要人物或事件', '概括段意要抓中心句和关键词，并关注人物、事件、原因、结果等主要信息。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '阅读短文时，概括段落大意通常应抓住哪些信息？');
