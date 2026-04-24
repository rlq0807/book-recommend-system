# 图书推荐系统
一个基于Spring Boot的智能图书推荐系统，集成了协同过滤推荐算法和冷启动优化策略。欢迎各大学生将其用于各种课设，有疑问或安装配置问题可通过邮箱联系作者。

## 项目简介
本系统是一个完整的图书推荐管理系统，主要功能包括：

- 用户管理（注册、登录、权限控制）
- 图书管理与借阅
- 智能推荐系统（协同过滤 + 冷启动优化）
- 借阅限制与逾期惩罚
- 邮件提醒
- 评分系统
## 技术栈
- 后端框架 ：Spring Boot 2.6.13
- 前端框架 ：Thymeleaf + Bootstrap 5
- 数据库 ：MySQL 8.0
- 推荐算法 ：协同过滤（UserCF + ItemCF）、偏置感知、分类偏好冷启动
- 邮件服务 ：Spring Mail
- 构建工具 ：Maven
## 核心功能
### 1. 用户管理
- 注册（支持设置偏好分类）
- 登录（基于Session）
- 权限控制（普通用户/管理员）
- 个人中心（修改密码、邮箱、偏好分类）
### 2. 图书管理
- 图书列表与详情
- 分类筛选
- 关键词搜索
- 评分与热度统计
### 3. 借阅管理
- 借阅流程（最多5本）
- 逾期惩罚（30天内逾期3次禁止借阅30天）
- 续借功能（每本可续借1次，延长7天）
- 邮件提醒（逾期自动发送）
### 4. 推荐系统
- 协同过滤 ：UserCF + ItemCF 混合
- 偏置感知 ：考虑用户偏置和物品偏置
- 冷启动 ：基于分类偏好的推荐
- 混合推荐 ：三阶段融合策略
### 5. 系统特色
- 多维度推荐算法评价（准确率、召回率、F1值、NDCG）
- 实时邮件提醒
- 响应式前端界面
- 完善的错误处理
## 快速开始
### 环境要求
- JDK 17+
- MySQL 8.0+
- Maven 3.6+
### 安装步骤
1. 克隆项目
   
   ```
   git clone https://github.com/
   rlq0807/
   book-recommend-system.git
   cd book-recommend-system
   ```
2. 配置数据库
   
   - 创建数据库 book_recommend_system
   - 修改 application.properties 中的数据库连接信息
3. 构建项目
   
   ```
   mvn clean package
   ```
4. 运行项目
   
   ```
   mvn spring-boot:run
   ```
5. 访问系统
   
   - 首页： http://localhost:8080
   - 登录页： http://localhost:8080/login
   - 管理员后台： http://localhost:8080/admin
## 推荐算法
### 算法架构
- UserCF ：基于用户相似度的推荐
- ItemCF ：基于物品相似度的推荐
- BiasAware ：考虑用户和物品偏置
- Hybrid ：三阶段融合策略
### 冷启动策略
- 基于用户偏好分类
- 热门混合推荐
- NDCG ：0.412
## 项目结构
```
book-recommend-system/
├── src/
│   ├── main/
│   │   ├── java/com/renlq/
bookrecommendsystem/
│   │   │   ├── controller/        
# 控制器
│   │   │   ├── entity/           # 
实体类
│   │   │   ├── interceptor/       
# 拦截器
│   │   │   ├── repository/        
# 数据访问
│   │   │   ├── service/           
# 业务逻辑
│   │   │   ├── util/              
# 工具类
│   │   │   └── 
BookRecommendSystemApplication.
java  # 启动类
│   │   └── resources/
│   │       ├── templates/          
# 前端模板
│   │       └── application.
properties  # 配置文件
├── pom.xml                         
# Maven配置
└── README.md                       
# 项目说明
```
## 配置说明
### 邮件配置
修改 application.properties 中的邮件配置：

```
spring.mail.host=smtp.qq.com
spring.mail.username=your-email@qq.
com
spring.mail.
password=your-email-password
spring.mail.properties.mail.smtp.
auth=true
spring.mail.properties.mail.smtp.
starttls.enable=true
spring.mail.properties.mail.smtp.
starttls.required=true
```
### 数据库配置
```
spring.datasource.url=jdbc:mysql://
localhost:3306/
book_recommend_system?useSSL=false&
serverTimezone=UTC
spring.datasource.username=root
spring.datasource.
password=your-password
```
## 贡献指南
1. Fork 本项目
2. 创建 feature 分支
3. 提交代码
4. 推送到远程分支
5. 创建 Pull Request

## 联系方式
- 项目维护者：[秋]
- 邮箱：[2323758014@qq.com ]
- GitHub： rlq0807
