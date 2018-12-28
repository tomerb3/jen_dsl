"# jen_dsl" 

 
git remote add origin https://github.com/tomerb3/jen_dsl.git
 
 
 c:\users\baum2\data\jen_dsl
 
 
docker run -d -p 8080:8080 --name jen1 -v ~/data/jen_dsl:/var/jenkins_home tomdesinto/jenkins-dsl-ready

http://192.168.99.100:8080/
