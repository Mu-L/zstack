ALTER TABLE `zstack`.`OAuth2ClientVO` ADD COLUMN `scope` varchar(255) default 'openid';

ALTER TABLE `zstack`.`OAuth2ClientVO` ADD COLUMN `identityProvider` varchar(32) default 'default';