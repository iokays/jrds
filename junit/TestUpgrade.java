import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import jrds.Probe;
import jrds.ProbeDesc;
import jrds.StoreOpener;
import jrds.Tools;
import jrds.mockobjects.DummyProbe;
import jrds.mockobjects.GenerateProbe;
import jrds.mockobjects.GetMoke;
import jrds.store.RrdDbStoreFactory;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rrd4j.DsType;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;


public class TestUpgrade {
    static final private Logger logger = Logger.getLogger(TestUpgrade.class);

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @BeforeClass
    static public void configure() throws ParserConfigurationException, IOException {
        Tools.configure();
        Tools.setLevel(new String[] {"jrds"}, logger.getLevel());
    }

    @Test
    public void bidule() throws Exception {
        
        StoreOpener.prepare(null);
        ProbeDesc pd = GetMoke.getPd();
        pd.add("MokeDs2", DsType.GAUGE);
        
        Probe<String,Number> p = GenerateProbe.quickProbe(testFolder);
        p.setPd(pd);
        p.setStep(300);
        p.setName("dummy");
        p.getMainStore().checkStoreFile();
        System.out.println(p.getMainStore());
        System.out.println(p.getMainStore().getStoreObject());

        RrdDef def = ((RrdDb) p.getMainStore().getStoreObject()).getRrdDef();
        def.setStep(300);
        RrdDb db = new RrdDb(def);
        long time = System.currentTimeMillis() / 1000;
        Sample s = db.createSample();
        s.set(time + ":1:1");
        s.update();
        time +=60;
        s.set(time + ":2:2");
        s.update();
        time +=60;
        s.set(time + ":3:3");
        s.update();
        time +=60;
        s.set(time + ":4:4");
        s.update();
        time +=60;
        s.set(time + ":5:5");
        s.update();

        p.checkStore();
    }
}
