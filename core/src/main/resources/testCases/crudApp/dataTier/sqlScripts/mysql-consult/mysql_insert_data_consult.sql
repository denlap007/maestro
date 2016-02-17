SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL';

USE consult;

SET AUTOCOMMIT=0;
INSERT INTO address (line1, line2, city, region, country, postal_code) VALUES ('100 Data Street', 'Suite 432', 'San Francisco', 'California', 'USA', '94103');
INSERT INTO client (client_name, client_department_number, billing_address, contact_email, contact_password) VALUES ('Big Data Corp.', 2000, 1, 'accounting@bigdatacorp.com', 'accounting');
INSERT INTO project (client_name, client_department_number, project_name, contact_email, contact_password) VALUES ('Big Data Corp.', 2000, 'Secret Project', 'project.manager@bigdatacorp.com', 'project.manager');
INSERT INTO recruiter (email, password, client_name, client_department_number) VALUES ('bob@jsfcrudconsultants.com', 'bob', 'Big Data Corp.', 2000);
INSERT INTO consultant_status (status_id, description) VALUES ('A', 'Active');
INSERT INTO consultant (status_id, email, password, hourly_rate, billable_hourly_rate, hire_date, recruiter_id) VALUES ('A', 'janet.smart@jsfcrudconsultants.com', 'janet.smart', 80, 120, '2007-2-15', 1);
INSERT INTO project_consultant (client_name, client_department_number, project_name, consultant_id) VALUES ('Big Data Corp.', 2000, 'Secret Project', 1);
INSERT INTO billable (consultant_id, client_name, client_department_number, project_name, start_date, end_date, hours, hourly_rate, billable_hourly_rate, description) VALUES (1, 'Big Data Corp.', 2000, 'Secret Project', '2008-10-13 00:00:00.0', '2008-10-17 00:00:00.0', 40, 80, 120, 'begin gathering requirements');
INSERT INTO billable (consultant_id, client_name, client_department_number, project_name, start_date, end_date, hours, hourly_rate, billable_hourly_rate, description) VALUES (1, 'Big Data Corp.', 2000, 'Secret Project', '2008-10-20 00:00:00.0', '2008-10-24 00:00:00.0', 40, 80, 120, 'finish gathering requirements');
COMMIT;

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;