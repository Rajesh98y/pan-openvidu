package pan;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class Filters
{
    public void json(HttpServletRequest req, HttpServletResponse res) throws Exception
    {
        res.setContentType("application/json;charset=utf-8");
    }
}
