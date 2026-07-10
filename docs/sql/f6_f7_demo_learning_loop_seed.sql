-- F6/F7 demo seed data for the complete learning loop.
-- Purpose: verify "assessment -> grading -> report -> profile update -> wrong book -> resource recommendation".
-- Usage:
--   mysql --default-character-set=utf8mb4 -uroot -proot smart_learning_system < docs/sql/f6_f7_demo_learning_loop_seed.sql

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO learning_resource
(resource_name, resource_type, subject, knowledge_point, textbook_version, file_url, file_size, status, create_time, update_time)
SELECT '一次函数基础讲义', 2, '数学', '一次函数', '通用版', 'https://example.com/smart-learning/demo/linear-function-notes.pdf', 524288, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_resource WHERE resource_name = '一次函数基础讲义');

INSERT INTO learning_resource
(resource_name, resource_type, subject, knowledge_point, textbook_version, file_url, file_size, status, create_time, update_time)
SELECT '二次函数图像微课', 1, '数学', '二次函数', '通用版', 'https://example.com/smart-learning/demo/quadratic-video', 1048576, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_resource WHERE resource_name = '二次函数图像微课');

INSERT INTO learning_resource
(resource_name, resource_type, subject, knowledge_point, textbook_version, file_url, file_size, status, create_time, update_time)
SELECT '勾股定理专项练习', 3, '数学', '勾股定理', '通用版', 'https://example.com/smart-learning/demo/pythagorean-practice.pdf', 786432, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_resource WHERE resource_name = '勾股定理专项练习');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 1, 1, '已知一次函数 y=2x+1，当 x=3 时，y 的值是多少？', '5|6|7|8', '7', '把 x=3 代入 y=2x+1，得 y=7。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '已知一次函数 y=2x+1，当 x=3 时，y 的值是多少？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 1, 1, '一次函数 y=3x-2 的图像与 y 轴交点纵坐标是？', '-3|-2|2|3', '-2', 'y 轴交点处 x=0，代入得 y=-2。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '一次函数 y=3x-2 的图像与 y 轴交点纵坐标是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 2, 1, '若点 (1,4) 在直线 y=kx+2 上，则 k 的值为？', '1|2|3|4', '2', '代入点坐标：4=k+2，所以 k=2。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '若点 (1,4) 在直线 y=kx+2 上，则 k 的值为？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 2, 3, '一次函数 y=-2x+6 与 x 轴交点坐标是什么？', '', '(3,0)', 'x 轴上 y=0，0=-2x+6，x=3，所以交点为 (3,0)。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '一次函数 y=-2x+6 与 x 轴交点坐标是什么？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '二次函数', 1, 1, '二次函数 y=x^2+2 的最小值是？', '0|1|2|3', '2', 'x^2 的最小值为 0，所以 y 的最小值为 2。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '二次函数 y=x^2+2 的最小值是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '二次函数', 1, 1, '抛物线 y=(x+1)^2 的顶点坐标是？', '(-1,0)|(1,0)|(0,-1)|(0,1)', '(-1,0)', '顶点式 y=(x-h)^2+k，此处 h=-1，k=0。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '抛物线 y=(x+1)^2 的顶点坐标是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '二次函数', 2, 1, '二次函数 y=x^2-4x+3 的对称轴是？', 'x=1|x=2|x=3|x=4', 'x=2', '对称轴 x=-b/(2a)=4/2=2。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '二次函数 y=x^2-4x+3 的对称轴是？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '勾股定理', 1, 1, '直角三角形两直角边为 6 和 8，斜边长为？', '9|10|11|12', '10', 'c^2=6^2+8^2=100，所以 c=10。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '直角三角形两直角边为 6 和 8，斜边长为？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '勾股定理', 2, 1, '直角三角形斜边为 13，一条直角边为 5，另一条直角边为？', '8|10|12|14', '12', '另一边平方为 13^2-5^2=144，所以边长为 12。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '直角三角形斜边为 13，一条直角边为 5，另一条直角边为？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '勾股定理', 2, 3, '判断边长 9、12、15 能否构成直角三角形？', '', '能', '9^2+12^2=81+144=225，15^2=225，所以能构成直角三角形。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '判断边长 9、12、15 能否构成直角三角形？');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '一次函数', 2, 4, '请说明一次函数 y=kx+b 中 k 和 b 对图像的影响。', '', 'k 决定直线的倾斜方向和陡峭程度，b 决定直线与 y 轴的交点。', '主观题重点看是否说明 k 的斜率作用，以及 b 的截距作用。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '请说明一次函数 y=kx+b 中 k 和 b 对图像的影响。');

INSERT INTO question_bank
(subject, knowledge_point, difficulty, question_type, question_text, options, answer, analysis, create_time, update_time)
SELECT '数学', '勾股定理', 2, 4, '请写出判断一个三角形是否为直角三角形的一种方法。', '', '比较最长边的平方是否等于另外两边平方和，如果相等，则是直角三角形。', '主观题关注是否使用勾股定理逆定理，并说明最长边与另外两边的关系。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM question_bank WHERE question_text = '请写出判断一个三角形是否为直角三角形的一种方法。');
