-- code_industry
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('건설업','건설업',TRUE);
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('운수창고통신업','운수창고통신업',FALSE);
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('전기가스증기수도사업','전기가스증기수도사업',FALSE);
INSERT INTO code_industry(industry,display_name,is_high_risk) VALUES ('제조업','제조업',TRUE);

-- code_size_class
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('5인 미만','5인 미만',0,4,1);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('5~9인','5~9인',5,9,2);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('10~19인','10~19인',10,19,3);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('20~29인','20~29인',20,29,4);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('30~49인','30~49인',30,49,5);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('50~99인','50~99인',50,99,6);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('100~299인','100~299인',100,299,7);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('300~499인','300~499인',300,499,8);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('500~999인','500~999인',500,999,9);
INSERT INTO code_size_class(size_class,display_name,min_employee,max_employee,sort_order) VALUES ('1,000인 이상','1,000인 이상',1000,NULL,10);

-- code_region
INSERT INTO code_region(region,display_name,is_target) VALUES ('강원','강원',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('경기','경기',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('경남','경남',TRUE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('경북','경북',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('광주','광주',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('대구','대구',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('대전','대전',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('부산','부산',TRUE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('서울','서울',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('울산','울산',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('인천','인천',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('전남','전남',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('전북','전북',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('제주','제주',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('충남','충남',FALSE);
INSERT INTO code_region(region,display_name,is_target) VALUES ('충북','충북',FALSE);

-- code_accident_type
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('감전','감전');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('기타','기타');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('깔림.뒤집힘','깔림.뒤집힘');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('끼임','끼임');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('넘어짐','넘어짐');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('동물상해','동물상해');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('떨어짐','떨어짐');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('무너짐','무너짐');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('물체에맞음','물체에맞음');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('부딪힘','부딪힘');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('분류불능','분류불능');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('불균형및무리한동작','불균형및무리한동작');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('빠짐익사','빠짐익사');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('사업장내교통사고','사업장내교통사고');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('사업장외교통사고','사업장외교통사고');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('산소결핍','산소결핍');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('업무상질병','업무상질병');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('이상온도물체접촉','이상온도물체접촉');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('절단베임찔림','절단베임찔림');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('체육행사','체육행사');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('폭력행위','폭력행위');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('폭발파열','폭발파열');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('화재','화재');
INSERT INTO code_accident_type(accident_type,display_name) VALUES ('화학물질누출접촉','화학물질누출접촉');
