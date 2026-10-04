package com.stocktracker.util;

import com.stocktracker.model.Company;
import com.stocktracker.model.Market;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyImporterTest {

    @Test
    void readsOurOwnFormatWithMarketColumnAndQuotedNames() throws IOException {
        String csv = "symbol,name,market\nTCS,\"Tata Consultancy, Services\",NSE\nAAPL,Apple Inc.,US\n";
        List<Company> list = CompanyImporter.parse(new StringReader(csv), Market.BSE);
        assertEquals(2, list.size());
        assertEquals("Tata Consultancy, Services", list.get(0).name());
        assertEquals(Market.NSE, list.get(0).market());
        assertEquals(Market.US, list.get(1).market());
    }

    @Test
    void readsNseEquityListAndKeepsOnlyEqAndBeSeries() throws IOException {
        String csv = "SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING\n"
                + "INFY,Infosys Limited,EQ,08-FEB-1995\n"
                + "XYZ,Some Bond Ltd,N1,01-JAN-2020\n"
                + "ABC,Another Ltd,BE,01-JAN-2010\n";
        List<Company> list = CompanyImporter.parse(new StringReader(csv), Market.NSE);
        assertEquals(List.of("INFY", "ABC"), list.stream().map(Company::symbol).toList());
        assertEquals(Market.NSE, list.get(0).market());
    }

    @Test
    void readsNasdaqPipeFileSkippingTestIssuesAndFooter() throws IOException {
        String txt = "Symbol|Security Name|Market Category|Test Issue|Financial Status\n"
                + "MSFT|Microsoft Corporation - Common Stock|Q|N|N\n"
                + "ZZTEST|Test Security|G|Y|N\n"
                + "File Creation Time: 1004202600:00|||||\n";
        List<Company> list = CompanyImporter.parse(new StringReader(txt), Market.US);
        assertEquals(1, list.size());
        assertEquals("MSFT", list.get(0).symbol());
        assertEquals(Market.US, list.get(0).market());
    }

    @Test
    void headerlessFileFallsBackToSymbolNameMarket() throws IOException {
        List<Company> list = CompanyImporter.parse(new StringReader("WIPRO,Wipro Ltd,NSE\n"), Market.US);
        assertEquals(1, list.size());
        assertEquals("WIPRO", list.get(0).symbol());
        assertEquals(Market.NSE, list.get(0).market());
    }
}
